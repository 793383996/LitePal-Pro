package com.litepaltest.test.annotation

import androidx.test.filters.SmallTest
import com.litepaltest.model.Cellphone
import com.litepaltest.test.LitePalTestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.litepal.LitePal
import java.util.UUID

@SmallTest
class ColumnTest : LitePalTestCase() {

    @Before
    fun setUp() {
        LitePal.getDatabase()
    }

    @Test
    fun testUnique() {
        val serial = UUID.randomUUID().toString()
        for (i in 0..1) {
            val cellphone = Cellphone()
            cellphone.brand = "三星"
            cellphone.inStock = 'Y'
            cellphone.price = 1949.99
            cellphone.serial = serial
            if (i == 0) {
                assertTrue(cellphone.save())
            } else if (i == 1) {
                expectFailureSilently {
                    assertFalse(cellphone.save())
                }
            }
        }
    }

    @Test
    fun testNotNull() {
        val cellphone = Cellphone()
        cellphone.brand = "三星"
        cellphone.inStock = 'Y'
        cellphone.price = 1949.99
        expectFailureSilently {
            assertFalse(cellphone.save())
        }
        cellphone.serial = UUID.randomUUID().toString()
        assertTrue(cellphone.save())
    }

    @Test
    fun testDefaultValue() {
        val cellphone = Cellphone()
        cellphone.brand = "三星"
        cellphone.inStock = 'Y'
        cellphone.price = 1949.99
        cellphone.serial = UUID.randomUUID().toString()
        assertTrue(cellphone.save())
        assertEquals("0.0.0.0", LitePal.find(Cellphone::class.java, cellphone.id ?: 0)?.mac)
        cellphone.mac = "192.168.0.1"
        assertTrue(cellphone.save())
        assertEquals("192.168.0.1", LitePal.find(Cellphone::class.java, cellphone.id ?: 0)?.mac)
    }
}
