package org.litepal.sampletest.suite.transaction_concurrency

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    TransactionConcurrencyInstrumentationTest::class
)
class TransactionConcurrencySuite
