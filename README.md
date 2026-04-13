# LitePal Pro (LitePal 4.x)

![Logo](https://github.com/LitePalFramework/LitePal/blob/master/sample/src/main/logo/mini_logo.png)

LitePal Pro 是面向 Android 的 Kotlin-first SQLite ORM，4.x 版本采用“编译期生成元数据 + 运行期直接消费”的主链路。

中文专题文档（按代码实况维护）：[`docs/README.md`](./docs/README.md)

---

## 中文版

### 1. 项目定位

LitePal Pro 4.x 的核心目标：

- 以 **Generated Metadata** 作为运行时单一真值。
- 将 schema 建模、字段映射、关联信息前移到编译期生成。
- 运行期走 generated-only 主链路，缺失生成产物直接 fail fast。
- API 继续保持同步语义，通过 runtime options 接入业务线程/协程策略。

### 2. 当前技术基线（来自仓库构建脚本）

- Android: `minSdk 24`, `compileSdk 36`（`sample` 为 `targetSdk 36`）
- Java/Kotlin bytecode target: `17`
- CI runtime: `JDK 21`
- Gradle Wrapper: `9.4.1`
- AGP: `9.1.0`
- Kotlin: `2.3.20`
- KSP plugin: `2.3.6`

### 3. 仓库模块与构建图

`settings.gradle` 当前主构建图：

- `:sample`
- `:sample-test`
- `:core`
- `:compiler-common`
- `:compiler-ksp`
- `:compiler-kapt`
- `:fixture-runtime-stubs`
- `:fixture-ksp-consumer`
- `:fixture-kapt-consumer`

职责概览：

- `core`: 运行时引擎（初始化、开库、CRUD、关联、事务、多库、监听、预热、Schema 校验）
- `compiler-common`: 编译器共享模型与代码渲染
- `compiler-ksp` / `compiler-kapt`: 编译期处理器实现
- `sample`: Compose 示例应用（演示 + Dashboard 自动化测试编排入口）
- `sample-test`: 独立 instrumentation 门禁模块（固定 8 套件）
- `fixture-*`: 处理器一致性与负例验证工程（KSP/KAPT parity + 诊断断言）

维护态目录（默认不在主构建图）：

- `compat-3x-*`: 3.x 迁移辅助
- `java` / `kotlin`: 历史发布目录

### 4. 最新核心架构（按代码）

#### 4.1 编译期生成链路

处理器扫描 `@LitePalSchemaAnchor`，并强约束：

- classpath 必须且只能有 1 个 Anchor。
- `entities` 不可为空。
- 持久化字段必须有 public getter/setter。

生成产物包括：

- `LitePalGeneratedRegistryImpl.kt`
- `schema-v{version}.json`
- `schema-hash.txt`
- `migration-diff-report.txt`
- `META-INF/services/org.litepal.generated.LitePalGeneratedRegistry`

#### 4.2 运行期主链路

- `LitePal.initialize(context)` 注入 ApplicationContext。
- `LitePal.getDatabase()` 进入 `Operator -> Connector`。
- `Connector` 在开库时做 epoch 一致性校验，配置漂移会重试（最多 5 次）并 fallback reopen。
- `LitePalOpenHelper.onConfigure` 默认启用 ForeignKey 和 WAL（失败降级 warning）。
- `SchemaValidationGate` 使用 schema-hash 快速路径，不匹配时执行列级校验（`STRICT` 抛错，`LOG` 告警）。
- `GeneratedRegistryLocator` 通过 ServiceLoader 装载 registry，缺失/重复均视为硬错误。

#### 4.3 并发与一致性模型

- 全局公平锁：`DatabaseRuntimeLock(ReentrantReadWriteLock(true))`。
- 事务上下文按线程绑定，支持嵌套深度。
- 多库切换 `use/useDefault/deleteDatabase` 在写锁区执行并递增 `dbConfigEpoch`。
- `preloadDatabase` 按 epoch 去重，配置切换时取消旧 epoch 预热任务。

#### 4.4 Runtime 默认策略

`LitePalRuntimeOptions` 默认值：

- `allowMainThreadAccess = false`
- `mainThreadViolationPolicy = MainThreadViolationPolicy.THROW`
- `queryExecutor = LitePalExecutors.defaultQueryExecutor()`（默认单线程）
- `transactionExecutor = LitePalExecutors.defaultTransactionExecutor()`（默认单线程）
- `schemaValidationMode = SchemaValidationMode.STRICT`

### 5. 快速接入（源码仓库模式）

同一模块内选择一种处理器路径：**KSP（推荐）或 KAPT（兼容）**。

#### 5.1 KSP（推荐）

```groovy
plugins {
    id 'com.android.application'
    id 'com.google.devtools.ksp'
}

dependencies {
    implementation project(':core')
    ksp project(':compiler-ksp')
}
```

#### 5.2 KAPT（可选）

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.kapt'
}

dependencies {
    implementation project(':core')
    kapt project(':compiler-kapt')
}
```

#### 5.3 声明唯一 Anchor

```kotlin
import org.litepal.annotation.LitePalSchemaAnchor

@LitePalSchemaAnchor(
    version = 1,
    entities = [Singer::class, Album::class, Song::class]
)
class AppSchemaAnchor
```

#### 5.4 实体示例

```kotlin
import org.litepal.annotation.Column
import org.litepal.annotation.Encrypt
import org.litepal.crud.LitePalSupport

class Song : LitePalSupport() {
    var id: Long = 0

    @Column(index = true)
    var name: String? = null

    @Column(unique = true, index = true)
    @Encrypt("AES")
    var lyric: String? = null

    var duration: String? = null
    var album: Album? = null
}
```

当前处理器支持持久化基础类型：

- `Boolean/Float/Double/Int/Long/Short/Char/String/Date`（含 Kotlin 对应装箱类型）

#### 5.5 `litepal.xml`（4.x 仅承载数据库配置）

```xml
<?xml version="1.0" encoding="utf-8"?>
<litepal>
    <dbname value="app" />
    <version value="1" />
    <cases value="lower" />
    <storage value="internal" />
</litepal>
```

说明：4.x 不再使用 `<list><mapping .../></list>` 作为实体真值来源。

#### 5.6 Application 初始化建议

```kotlin
LitePal.initialize(this)

LitePal.setRuntimeOptions(
    LitePalRuntimeOptions(
        allowMainThreadAccess = false,
        mainThreadViolationPolicy = MainThreadViolationPolicy.THROW,
        queryExecutor = queryExecutor,
        transactionExecutor = transactionExecutor,
        schemaValidationMode = SchemaValidationMode.STRICT
    )
)

LitePal.setErrorPolicy(LitePalErrorPolicy.STRICT)
LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
```

### 6. Sample 与测试门禁架构

#### 6.1 `sample`（应用演示模块）

- `MyApplication` 仅负责 LitePal 初始化和 runtime policy 注入，不直接跑压测。
- `MainActivity` 绑定双 ViewModel：`SampleDataViewModel` + `TestDashboardViewModel`。
- `onCreate` 自动触发一次 Full 套件：`startAutoFullRunIfNeeded()`。
- UI 结构按功能拆分：`Home/Crud/Aggregate/Tables/Structure/Diagnostics`。
- Dashboard 事件流：`RunStarted/CaseStarted/Checkpoint/CasePassed/CaseFailed/RunCancelled/RunFinished/RunCrashed`。

#### 6.2 `sample-test`（独立 instrumentation 门禁模块）

默认 instrumentation 入口：

- `org.litepal.sampletest.suite.SampleTestAllSuites`

固定 8 套件：

1. `boot_open`
2. `crud_relation`
3. `transaction_concurrency`
4. `multi_db_epoch`
5. `listener_preload`
6. `schema_migration_validation`
7. `dashboard_orchestration`
8. `stress_full`

`SampleTestRuntimeBootstrap` 统一测试运行时策略；`sample-test/src/main/AndroidManifest.xml` 移除了 `androidx.startup.InitializationProvider` 以避免初始化冲突。

#### 6.3 根任务与 CI

- 根任务：`sampleTestAll`
- 任务链：`:sample:assembleDebug -> :sample:installDebug -> :sample-test:connectedDebugAndroidTest`
- CI：`.github/workflows/sample-pr-gate.yml`

### 7. 常用验证命令

```bash
# 核心构建与门禁
./gradlew \
  :compiler-common:test \
  :core:assembleDebug \
  :core:lint \
  :core:testDebugUnitTest \
  verifyGeneratedOnlyRuntimeGuards \
  verifyKspKaptGeneratedParity \
  verifyProcessorNegativeCases

# Sample + sample-test 全门禁
./gradlew sampleTestAll --stacktrace

# 只跑 sample-test 全套
./gradlew :sample-test:connectedDebugAndroidTest --stacktrace

# 单套件示例（Dashboard 编排）
./gradlew :sample-test:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=org.litepal.sampletest.suite.dashboard_orchestration.DashboardOrchestrationSuite" \
  --stacktrace

# 启动稳定性耗时统计脚本
python tools/startup_stability_timing_stats.py \
  --input sample-test/build/outputs/androidTest-results/connected/debug \
  --run-id latest_success
```

### 8. 发布与制品

当前 `core` 模块内置发布相关任务：

- `:core:publishReleasePublicationToMavenLocal`
- `:core:publishReleasePublicationToSonatypeRepository`
- `:core:submitReleaseToCentralPortal`
- `:core:publishReleaseToCentralPortal`

发布前会校验 `CENTRAL_* / OSSRH_* / SIGNING_*` 凭据。

### 9. 4.x 迁移提示

- 旧 async API（例如 `findAsync/countAsync`）已移除。
- 推荐模式：同步 ORM API + 业务层协程/执行器调度。
- `compat-3x-*` 为迁移辅助目录，不建议新项目依赖。

### 10. 文档索引

- 文档总览：[`docs/README.md`](./docs/README.md)
- 快速接入：[`docs/getting-started.md`](./docs/getting-started.md)
- CRUD、查询与事务：[`docs/crud-query-transaction.md`](./docs/crud-query-transaction.md)
- 运行时选项与并发：[`docs/runtime-options-and-concurrency.md`](./docs/runtime-options-and-concurrency.md)
- 多库、监听、预热与 Schema 校验：[`docs/multi-db-listener-and-schema-validation.md`](./docs/multi-db-listener-and-schema-validation.md)
- 测试与 CI 门禁：[`docs/testing-and-ci.md`](./docs/testing-and-ci.md)
- 排错与 3.x -> 4.x 迁移：[`docs/troubleshooting-and-migration.md`](./docs/troubleshooting-and-migration.md)

---

## English Version (Brief)

### 1. What LitePal Pro 4.x is

LitePal Pro 4.x is a Kotlin-first Android SQLite ORM with a generated-metadata-first architecture:

- Compile-time processors (`compiler-ksp` / `compiler-kapt`) generate registry/schema/mapping artifacts.
- Runtime `core` consumes generated artifacts directly (generated-only main path).
- APIs stay synchronous; scheduling is configurable via `LitePalRuntimeOptions`.

### 2. Baseline

- Android: `minSdk 24`, `compileSdk 36`, sample `targetSdk 36`
- Java/Kotlin bytecode target: `17`
- Gradle Wrapper: `9.4.1`
- AGP: `9.1.0`
- Kotlin: `2.3.20`
- KSP: `2.3.6`
- CI JDK: `21`

### 3. Active module graph (`settings.gradle`)

- `sample`, `sample-test`, `core`
- `compiler-common`, `compiler-ksp`, `compiler-kapt`
- `fixture-runtime-stubs`, `fixture-ksp-consumer`, `fixture-kapt-consumer`

Maintenance-only directories (not in default graph): `compat-3x-*`, `java`, `kotlin`.

### 4. Quick wiring

Use one processor path per module.

```groovy
// KSP (recommended)
implementation project(':core')
ksp project(':compiler-ksp')

// KAPT (optional)
implementation project(':core')
kapt project(':compiler-kapt')
```

Declare exactly one `@LitePalSchemaAnchor`, then initialize in `Application.onCreate()`:

```kotlin
LitePal.initialize(this)
LitePal.setRuntimeOptions(...)
LitePal.setErrorPolicy(LitePalErrorPolicy.STRICT)
LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
```

### 5. Sample and test gate

- `sample`: Compose demo + dashboard orchestration entry.
- `sample-test`: dedicated instrumentation gate with 8 suites.
- Root gate task: `./gradlew sampleTestAll --stacktrace`.

### 6. Docs

See [`docs/README.md`](./docs/README.md) for full Chinese deep-dive docs and maintenance guides.
