package org.litepal.sampletest.suite.boot_open

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.litepal.sampletest.SampleCrudIntegrationInstrumentationTest
import org.litepal.sampletest.SampleRuntimeOptionsInstrumentationTest

@RunWith(Suite::class)
@Suite.SuiteClasses(
    SampleRuntimeOptionsInstrumentationTest::class,
    SampleCrudIntegrationInstrumentationTest::class
)
class BootOpenSuite
