package org.litepal.sampletest.suite.crud_relation

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.litepal.sampletest.SampleCrudIntegrationInstrumentationTest

@RunWith(Suite::class)
@Suite.SuiteClasses(
    SampleCrudIntegrationInstrumentationTest::class
)
class CrudRelationSuite
