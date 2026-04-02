package org.litepal.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal

@RunWith(AndroidJUnit4::class)
class SharedUtilInstrumentedTest {

    private val keyWithSuffix = "shared_util_case.db"
    private val keyWithoutSuffix = "shared_util_case"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LitePal.initialize(context)
        SharedUtil.removeVersion(keyWithSuffix)
    }

    @After
    fun tearDown() {
        SharedUtil.removeVersion(keyWithSuffix)
    }

    @Test
    fun update_get_and_remove_version_work_as_expected() {
        SharedUtil.updateVersion(keyWithSuffix, 7)

        assertEquals(7, SharedUtil.getLastVersion(keyWithSuffix))

        SharedUtil.removeVersion(keyWithSuffix)

        assertEquals(0, SharedUtil.getLastVersion(keyWithSuffix))
    }

    @Test
    fun extra_key_name_with_db_suffix_is_normalized() {
        SharedUtil.updateVersion(keyWithSuffix, 9)

        assertEquals(9, SharedUtil.getLastVersion(keyWithSuffix))
        assertEquals(9, SharedUtil.getLastVersion(keyWithoutSuffix))
    }
}
