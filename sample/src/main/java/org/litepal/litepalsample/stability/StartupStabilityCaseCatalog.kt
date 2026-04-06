package org.litepal.litepalsample.stability

internal object StartupStabilityCaseCatalog {
    const val SAVE_ASSOCIATION_BASIC = "save_association_basic"
    const val QUERY_AGGREGATE_BASIC = "query_aggregate_basic"
    const val UPDATE_DELETE_BASIC = "update_delete_basic"
    const val TRANSACTION_COMMIT_BASIC = "transaction_commit_basic"
    const val TRANSACTION_ROLLBACK_BASIC = "transaction_rollback_basic"
    const val STRESS_BULK_INSERT_QUERY = "stress_bulk_insert_query"
    const val STRESS_BULK_UPDATE_DELETE = "stress_bulk_update_delete"
    const val STRESS_ASSOCIATION_HIGH_VOLUME = "stress_association_high_volume"
    const val STRESS_TRANSACTION_REPEAT = "stress_transaction_repeat"
    const val STRESS_UNIQUE_CONFLICT_ROLLBACK = "stress_unique_conflict_rollback"
    const val STRESS_CONCURRENT_READ_WRITE = "stress_concurrent_read_write"

    val orderedCaseNames: List<String> = listOf(
        SAVE_ASSOCIATION_BASIC,
        QUERY_AGGREGATE_BASIC,
        UPDATE_DELETE_BASIC,
        TRANSACTION_COMMIT_BASIC,
        TRANSACTION_ROLLBACK_BASIC,
        STRESS_BULK_INSERT_QUERY,
        STRESS_BULK_UPDATE_DELETE,
        STRESS_ASSOCIATION_HIGH_VOLUME,
        STRESS_TRANSACTION_REPEAT,
        STRESS_UNIQUE_CONFLICT_ROLLBACK,
        STRESS_CONCURRENT_READ_WRITE
    )
}
