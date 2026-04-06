package org.litepal.sampletest.test.util

import androidx.test.filters.SmallTest
import org.litepal.sampletest.test.LitePalTestCase
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.litepal.util.BaseUtility

@SmallTest
class BaseUtilityTest : LitePalTestCase() {

    @Test
    fun testCount() {
        val string = " This is a good one. That is a bad one. "
        val markThis = "This"
        val markIs = "is"
        val markA = "a"
        val markGood = "good"
        val markOne = "one"
        val markPoint = "."
        val markSpace = " "
        val markThat = "That"
        val markBad = "bad"
        val markNone = "none"
        val markEmpty = ""
        val markNull: String? = null
        assertEquals(1, BaseUtility.count(string, markThis))
        assertEquals(3, BaseUtility.count(string, markIs))
        assertEquals(4, BaseUtility.count(string, markA))
        assertEquals(1, BaseUtility.count(string, markGood))
        assertEquals(2, BaseUtility.count(string, markOne))
        assertEquals(2, BaseUtility.count(string, markPoint))
        assertEquals(11, BaseUtility.count(string, markSpace))
        assertEquals(1, BaseUtility.count(string, markThat))
        assertEquals(1, BaseUtility.count(string, markBad))
        assertEquals(0, BaseUtility.count(string, markNone))
        assertEquals(0, BaseUtility.count(string, markEmpty))
        assertEquals(0, BaseUtility.count(string, markNull))
    }
}




