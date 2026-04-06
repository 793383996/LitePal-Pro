package org.litepal.sampletest.suite

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.litepal.sampletest.suite.boot_open.BootOpenSuite
import org.litepal.sampletest.suite.crud_relation.CrudRelationSuite
import org.litepal.sampletest.suite.dashboard_orchestration.DashboardOrchestrationSuite
import org.litepal.sampletest.suite.listener_preload.ListenerPreloadSuite
import org.litepal.sampletest.suite.multi_db_epoch.MultiDbEpochSuite
import org.litepal.sampletest.suite.schema_migration_validation.SchemaMigrationValidationSuite
import org.litepal.sampletest.suite.stress_full.StressFullSuite
import org.litepal.sampletest.suite.transaction_concurrency.TransactionConcurrencySuite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    BootOpenSuite::class,
    CrudRelationSuite::class,
    TransactionConcurrencySuite::class,
    MultiDbEpochSuite::class,
    ListenerPreloadSuite::class,
    SchemaMigrationValidationSuite::class,
    DashboardOrchestrationSuite::class,
    StressFullSuite::class
)
class SampleTestAllSuites
