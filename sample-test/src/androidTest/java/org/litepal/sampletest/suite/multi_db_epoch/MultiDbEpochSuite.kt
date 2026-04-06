package org.litepal.sampletest.suite.multi_db_epoch

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    MultiDbEpochInstrumentationTest::class
)
class MultiDbEpochSuite
