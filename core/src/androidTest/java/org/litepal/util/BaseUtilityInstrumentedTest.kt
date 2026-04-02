package org.litepal.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.exceptions.LitePalSupportException

@RunWith(AndroidJUnit4::class)
class BaseUtilityInstrumentedTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LitePal.initialize(context)
    }

    @Test
    fun count_handles_normal_and_edge_inputs() {
        val text = " This is a good one. That is a bad one. "

        assertEquals(1, BaseUtility.count(text, "This"))
        assertEquals(3, BaseUtility.count(text, "is"))
        assertEquals(4, BaseUtility.count(text, "a"))
        assertEquals(0, BaseUtility.count(text, "none"))
        assertEquals(0, BaseUtility.count(text, ""))
        assertEquals(0, BaseUtility.count(text, null))
    }

    @Test
    fun capitalize_handles_normal_empty_and_null_inputs() {
        assertEquals("Litepal", BaseUtility.capitalize("litepal"))
        assertEquals("", BaseUtility.capitalize(""))
        assertNull(BaseUtility.capitalize(null))
    }

    @Test
    fun checkConditionsCorrect_passes_when_placeholders_match() {
        BaseUtility.checkConditionsCorrect("name = ? and age > ?", "Tom", "12")
    }

    @Test
    fun checkConditionsCorrect_throws_when_placeholders_do_not_match() {
        assertThrows(LitePalSupportException::class.java) {
            BaseUtility.checkConditionsCorrect("name = ? and age > ?", "Tom")
        }
    }
}
