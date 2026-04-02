@file:Suppress("DEPRECATION")

package org.litepal.compat.v3.config

import org.litepal.parser.LitePalAttr

@Deprecated(
    message = "compat-3x-config is in maintenance mode. Prefer LitePal 4.x configuration directly."
)
enum class IssueSeverity {
    INFO,
    WARNING
}

@Deprecated(
    message = "compat-3x-config is in maintenance mode and scheduled for future major removal."
)
data class ConfigHealthIssue(
    val code: String,
    val severity: IssueSeverity,
    val message: String
)

@Deprecated(
    message = "compat-3x-config is in maintenance mode and scheduled for future major removal."
)
data class ConfigHealthReport(
    val normalizedStorage: String,
    val issues: List<ConfigHealthIssue>
)

/**
 * Compatibility analyzer for 3.x `litepal.xml` values.
 */
@Deprecated(
    message = "compat-3x-config is in maintenance mode. Use for migration diagnostics only."
)
object Compat3xConfigAnalyzer {

    @JvmStatic
    fun analyzeCurrentConfig(): ConfigHealthReport {
        val currentStorage = LitePalAttr.getInstance().storage
        return analyzeStorage(currentStorage)
    }

    @JvmStatic
    fun analyzeStorage(storage: String?): ConfigHealthReport {
        val issues = mutableListOf<ConfigHealthIssue>()
        val normalized = when {
            storage.isNullOrBlank() -> {
                issues += ConfigHealthIssue(
                    code = "CFG-100",
                    severity = IssueSeverity.INFO,
                    message = "storage is empty, defaults to internal."
                )
                "internal"
            }
            storage.equals("internal", ignoreCase = true) -> "internal"
            storage.equals("external", ignoreCase = true) -> {
                issues += ConfigHealthIssue(
                    code = "CFG-101",
                    severity = IssueSeverity.WARNING,
                    message = "external storage now resolves to app-scoped external files directory."
                )
                "external"
            }
            else -> {
                val normalizedPath = storage
                    .replace('\\', '/')
                    .trim()
                    .removePrefix("/")
                    .replace("../", "")
                issues += ConfigHealthIssue(
                    code = "CFG-102",
                    severity = IssueSeverity.WARNING,
                    message = "legacy custom storage '$storage' normalized to app-scoped path '$normalizedPath'."
                )
                normalizedPath
            }
        }
        return ConfigHealthReport(normalizedStorage = normalized, issues = issues)
    }
}
