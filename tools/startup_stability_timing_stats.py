#!/usr/bin/env python3
"""Aggregate LitePal startup stability timing metrics from logcat text files.

Supports both legacy logs (checkpoint list embedded in CASE_RESULT) and
enhanced logs (CHECKPOINT_TIMING and METHOD_SUMMARY lines).
"""

from __future__ import annotations

import argparse
import json
import math
import re
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple


TAG = "LitePalStartupStability:"

CASE_ORDER = [
    "save_association_basic",
    "query_aggregate_basic",
    "update_delete_basic",
    "transaction_commit_basic",
    "transaction_rollback_basic",
    "stress_bulk_insert_query",
    "stress_bulk_update_delete",
    "stress_association_high_volume",
    "stress_transaction_repeat",
    "stress_unique_conflict_rollback",
    "stress_concurrent_read_write",
]

METHOD_LABELS = {
    "lp_save": "LitePalSupport.save",
    "lp_save_all": "LitePal.saveAll",
    "lp_find": "LitePal.find/findAll",
    "lp_find_first": "LitePal.findFirst",
    "lp_count": "LitePal.count",
    "lp_max": "LitePal.max",
    "lp_min": "LitePal.min",
    "lp_sum": "LitePal.sum",
    "lp_average": "LitePal.average",
    "lp_update": "LitePalSupport.update",
    "lp_update_all": "LitePal.updateAll",
    "lp_delete": "LitePal.delete",
    "lp_delete_all": "LitePal.deleteAll",
    "lp_run_in_tx": "LitePal.runInTransaction",
    "lp_concurrency": "Concurrent mixed flow",
    # Legacy checkpoint keys from historical logs.
    "load_album_eager": "LitePal.find(Album, eager=true)",
    "bulk_save": "LitePal.saveAll (bulk insert)",
    "bulk_count": "LitePal.count (bulk verify)",
    "save_singers": "LitePal.saveAll (association singers)",
    "save_albums": "LitePal.saveAll (association albums)",
    "save_songs": "LitePal.saveAll (association songs)",
    "save_conflict_batch": "LitePal.saveAll (conflict rollback)",
    "concurrent_run": "Concurrent mixed flow",
}


@dataclass(frozen=True)
class CaseRecord:
    run_id: str
    case_name: str
    success: bool
    cost_ms: int


@dataclass(frozen=True)
class CheckpointRecord:
    run_id: str
    case_name: str
    checkpoint: str
    method: str
    cost_ms: int
    source: str


@dataclass(frozen=True)
class RunSummary:
    run_id: str
    total_cases: int
    passed: int
    failed: int
    cancelled: bool
    pending: int
    total_ms: int
    avg_ms: int
    min_ms: int
    max_ms: int
    p50_ms: int
    p95_ms: int

    @property
    def is_full_success(self) -> bool:
        return (
            self.total_cases > 0
            and self.passed == self.total_cases
            and self.failed == 0
            and not self.cancelled
            and self.pending == 0
        )


def parse_bool(value: str) -> bool:
    return value.lower() == "true"


def parse_int(value: str, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def split_event_payload(payload: str) -> Tuple[str, Dict[str, str]]:
    parts = payload.strip().split("|")
    event = parts[0] if parts else ""
    kv: Dict[str, str] = {}
    for part in parts[1:]:
        if "=" not in part:
            continue
        key, value = part.split("=", 1)
        kv[key] = value
    return event, kv


def extract_payload(line: str) -> Optional[str]:
    if TAG in line:
        return line.split(TAG, 1)[1].strip()
    stripped = line.strip()
    if re.match(r"^(RUN_|CASE_|CHECKPOINT_|METHOD_)", stripped):
        return stripped
    return None


def derive_method_key(checkpoint: str) -> str:
    if "__" in checkpoint:
        return checkpoint.split("__", 1)[0]
    return checkpoint


def parse_checkpoint_list(
    run_id: str, case_name: str, raw: str, source: str = "case_result"
) -> List[CheckpointRecord]:
    if not raw:
        return []
    records: List[CheckpointRecord] = []
    for item in raw.split(","):
        token = item.strip()
        if not token or ":" not in token:
            continue
        checkpoint, cost_token = token.rsplit(":", 1)
        cost_ms = parse_int(cost_token.removesuffix("ms").strip(), default=-1)
        if cost_ms < 0:
            continue
        method = derive_method_key(checkpoint)
        records.append(
            CheckpointRecord(
                run_id=run_id,
                case_name=case_name,
                checkpoint=checkpoint,
                method=method,
                cost_ms=cost_ms,
                source=source,
            )
        )
    return records


def iter_log_files(input_path: Path) -> Iterable[Path]:
    if input_path.is_file():
        yield input_path
        return
    for pattern in ("*.txt", "*.log", "*.out"):
        yield from input_path.rglob(pattern)


def collect_records(
    input_path: Path,
) -> Tuple[List[CaseRecord], List[CheckpointRecord], Dict[str, RunSummary], List[Path]]:
    cases: List[CaseRecord] = []
    checkpoints: List[CheckpointRecord] = []
    summaries: Dict[str, RunSummary] = {}
    scanned_files: List[Path] = []

    for file in iter_log_files(input_path):
        try:
            text = file.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        scanned_files.append(file)
        for line in text.splitlines():
            payload = extract_payload(line)
            if not payload:
                continue
            event, kv = split_event_payload(payload)
            if event == "CASE_RESULT":
                run_id = kv.get("runId", "")
                case_name = kv.get("caseName", "")
                if not run_id or not case_name:
                    continue
                cases.append(
                    CaseRecord(
                        run_id=run_id,
                        case_name=case_name,
                        success=parse_bool(kv.get("success", "false")),
                        cost_ms=parse_int(kv.get("costMs", "0")),
                    )
                )
                checkpoints.extend(
                    parse_checkpoint_list(
                        run_id=run_id,
                        case_name=case_name,
                        raw=kv.get("checkpoints", ""),
                        source="case_result",
                    )
                )
            elif event == "CHECKPOINT_TIMING":
                run_id = kv.get("runId", "")
                case_name = kv.get("caseName", "")
                checkpoint = kv.get("checkpoint", "")
                if not run_id or not case_name or not checkpoint:
                    continue
                checkpoints.append(
                    CheckpointRecord(
                        run_id=run_id,
                        case_name=case_name,
                        checkpoint=checkpoint,
                        method=kv.get("method", derive_method_key(checkpoint)),
                        cost_ms=parse_int(kv.get("costMs", "0")),
                        source="checkpoint_timing",
                    )
                )
            elif event == "RUN_SUMMARY":
                run_id = kv.get("runId", "")
                if not run_id:
                    continue
                summaries[run_id] = RunSummary(
                    run_id=run_id,
                    total_cases=parse_int(kv.get("totalCases", "0")),
                    passed=parse_int(kv.get("passed", "0")),
                    failed=parse_int(kv.get("failed", "0")),
                    cancelled=parse_bool(kv.get("cancelled", "false")),
                    pending=parse_int(kv.get("pending", "0")),
                    total_ms=parse_int(kv.get("totalMs", "0")),
                    avg_ms=parse_int(kv.get("avgMs", "0")),
                    min_ms=parse_int(kv.get("minMs", "0")),
                    max_ms=parse_int(kv.get("maxMs", "0")),
                    p50_ms=parse_int(kv.get("p50", "0")),
                    p95_ms=parse_int(kv.get("p95", "0")),
                )
    return cases, checkpoints, summaries, scanned_files


def pick_run_ids(
    summaries: Dict[str, RunSummary],
    cases: List[CaseRecord],
    run_id_arg: Optional[str],
    success_only: bool,
) -> List[str]:
    all_run_ids = sorted({c.run_id for c in cases})
    if run_id_arg and run_id_arg != "latest_success":
        return [run_id_arg]

    candidate = all_run_ids
    if success_only:
        candidate = [rid for rid in all_run_ids if summaries.get(rid, RunSummary(rid, 0, 0, 0, True, 0, 0, 0, 0, 0, 0, 0)).is_full_success]

    if run_id_arg == "latest_success":
        return candidate[-1:] if candidate else []
    return candidate


def dedupe_checkpoints(records: Iterable[CheckpointRecord]) -> List[CheckpointRecord]:
    priority = {"case_result": 0, "checkpoint_timing": 1}
    chosen: Dict[Tuple[str, str, str, int], CheckpointRecord] = {}
    for rec in records:
        key = (rec.run_id, rec.case_name, rec.checkpoint, rec.cost_ms)
        prev = chosen.get(key)
        if prev is None or priority.get(rec.source, -1) > priority.get(prev.source, -1):
            chosen[key] = rec
    return list(chosen.values())


def compute_stats(values: List[int]) -> Dict[str, float]:
    if not values:
        return {"samples": 0, "avg": 0.0, "min": 0.0, "max": 0.0}
    return {
        "samples": float(len(values)),
        "avg": float(sum(values)) / len(values),
        "min": float(min(values)),
        "max": float(max(values)),
    }


def format_ms(value: float) -> str:
    if math.isclose(value, round(value), abs_tol=1e-9):
        return str(int(round(value)))
    return f"{value:.2f}"


def build_markdown(
    selected_run_ids: List[str],
    run_summaries: List[RunSummary],
    case_records: List[CaseRecord],
    checkpoint_records: List[CheckpointRecord],
    source_path: Path,
) -> str:
    case_group: Dict[str, List[int]] = defaultdict(list)
    for rec in case_records:
        if rec.success:
            case_group[rec.case_name].append(rec.cost_ms)

    method_group: Dict[str, List[int]] = defaultdict(list)
    for rec in checkpoint_records:
        method_group[rec.method].append(rec.cost_ms)

    checkpoint_group: Dict[Tuple[str, str], List[int]] = defaultdict(list)
    for rec in checkpoint_records:
        checkpoint_group[(rec.checkpoint, rec.method)].append(rec.cost_ms)

    lines: List[str] = []
    lines.append("### Startup Stability Timing Snapshot")
    lines.append(f"- Source log path: `{source_path}`")
    lines.append(f"- Selected successful runs: `{len(selected_run_ids)}`")
    if selected_run_ids:
        lines.append(f"- Run IDs: `{', '.join(selected_run_ids)}`")
    if run_summaries:
        avg_total = sum(r.total_ms for r in run_summaries) / len(run_summaries)
        lines.append(f"- Avg full-run total: `{format_ms(avg_total)}ms`")
    lines.append("")

    lines.append("#### Case Average Cost")
    lines.append("| Case | Samples | Avg(ms) | Min(ms) | Max(ms) |")
    lines.append("| --- | ---: | ---: | ---: | ---: |")
    ordered_cases = [c for c in CASE_ORDER if c in case_group] + sorted(set(case_group.keys()) - set(CASE_ORDER))
    for case_name in ordered_cases:
        stats = compute_stats(case_group[case_name])
        lines.append(
            f"| {case_name} | {int(stats['samples'])} | {format_ms(stats['avg'])} | {format_ms(stats['min'])} | {format_ms(stats['max'])} |"
        )
    lines.append("")

    lines.append("#### Core Method Average Cost")
    lines.append("| Method Key | Method | Samples | Avg(ms) | Min(ms) | Max(ms) |")
    lines.append("| --- | --- | ---: | ---: | ---: | ---: |")
    for method in sorted(method_group.keys(), key=lambda m: compute_stats(method_group[m])["avg"], reverse=True):
        stats = compute_stats(method_group[method])
        lines.append(
            f"| {method} | {METHOD_LABELS.get(method, method)} | {int(stats['samples'])} | {format_ms(stats['avg'])} | {format_ms(stats['min'])} | {format_ms(stats['max'])} |"
        )
    lines.append("")

    lines.append("#### Checkpoint Average Cost")
    lines.append("| Checkpoint | Method Key | Samples | Avg(ms) |")
    lines.append("| --- | --- | ---: | ---: |")
    for checkpoint, method in sorted(
        checkpoint_group.keys(),
        key=lambda k: compute_stats(checkpoint_group[k])["avg"],
        reverse=True,
    ):
        stats = compute_stats(checkpoint_group[(checkpoint, method)])
        lines.append(
            f"| {checkpoint} | {method} | {int(stats['samples'])} | {format_ms(stats['avg'])} |"
        )

    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize startup stability timing from logcat files.")
    parser.add_argument(
        "--input",
        default="sample-test/build/outputs/androidTest-results/connected/debug",
        help="Log file or directory to scan.",
    )
    parser.add_argument(
        "--run-id",
        default="latest_success",
        help="Specific runId to select, or latest_success (default).",
    )
    parser.add_argument(
        "--include-non-success",
        action="store_true",
        help="Include failed/cancelled/incomplete runs in aggregation.",
    )
    parser.add_argument("--output-json", help="Optional path to write JSON report.")
    parser.add_argument("--output-markdown", help="Optional path to write markdown report.")
    args = parser.parse_args()

    input_path = Path(args.input).resolve()
    cases, checkpoints, summaries, scanned_files = collect_records(input_path)
    if not scanned_files:
        raise SystemExit(f"No log files found under: {input_path}")

    success_only = not args.include_non_success
    selected_run_ids = pick_run_ids(
        summaries=summaries,
        cases=cases,
        run_id_arg=args.run_id,
        success_only=success_only,
    )
    if not selected_run_ids:
        raise SystemExit("No matching runs after filtering. Try --include-non-success or specify --run-id.")

    selected_case_records = [c for c in cases if c.run_id in set(selected_run_ids)]
    selected_case_records = [c for c in selected_case_records if c.success or not success_only]
    selected_checkpoint_records = [c for c in checkpoints if c.run_id in set(selected_run_ids)]
    selected_checkpoint_records = dedupe_checkpoints(selected_checkpoint_records)
    selected_run_summaries = [s for rid, s in summaries.items() if rid in set(selected_run_ids)]

    markdown = build_markdown(
        selected_run_ids=selected_run_ids,
        run_summaries=selected_run_summaries,
        case_records=selected_case_records,
        checkpoint_records=selected_checkpoint_records,
        source_path=input_path,
    )

    payload = {
        "source_path": str(input_path),
        "scanned_file_count": len(scanned_files),
        "selected_run_ids": selected_run_ids,
        "run_summaries": [s.__dict__ for s in selected_run_summaries],
        "case_records": [c.__dict__ for c in selected_case_records],
        "checkpoint_records": [c.__dict__ for c in selected_checkpoint_records],
        "markdown": markdown,
    }

    if args.output_json:
        out_json = Path(args.output_json)
        out_json.parent.mkdir(parents=True, exist_ok=True)
        out_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    if args.output_markdown:
        out_md = Path(args.output_markdown)
        out_md.parent.mkdir(parents=True, exist_ok=True)
        out_md.write_text(markdown + "\n", encoding="utf-8")

    print(markdown)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
