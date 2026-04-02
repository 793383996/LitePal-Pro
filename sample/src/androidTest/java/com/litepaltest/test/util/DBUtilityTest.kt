package com.litepaltest.test.util

import android.database.sqlite.SQLiteDatabase
import androidx.test.filters.SmallTest
import com.litepaltest.model.Book
import com.litepaltest.model.Cellphone
import com.litepaltest.test.LitePalTestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.litepal.tablemanager.Connector
import org.litepal.util.DBUtility

@SmallTest
class DBUtilityTest : LitePalTestCase() {

    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = Connector.getDatabase()
    }

    @Test
    fun testFindIndexedColumns() {
        var pair = DBUtility.findIndexedColumns(
            DBUtility.getTableNameByClassName(Cellphone::class.java.name),
            db
        )
        var indexColumns = pair.first
        var uniqueColumns = pair.second
        assertEquals(1, indexColumns.size)
        assertEquals(1, uniqueColumns.size)
        assertTrue(indexColumns.contains("brand"))
        assertTrue(uniqueColumns.contains("serial"))
        pair = DBUtility.findIndexedColumns(
            DBUtility.getTableNameByClassName(Book::class.java.name),
            db
        )
        indexColumns = pair.first
        uniqueColumns = pair.second
        assertEquals(0, indexColumns.size)
        assertEquals(0, uniqueColumns.size)
    }
}
