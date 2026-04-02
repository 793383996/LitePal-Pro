package com.litepaltest.test.crud.query

import android.database.Cursor
import androidx.test.filters.SmallTest
import com.litepaltest.model.Book
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assert.fail
import org.litepal.LitePal
import org.litepal.exceptions.DataSupportException
import org.litepal.util.DBUtility

@SmallTest
class QueryBySQLTest {

    private lateinit var book: Book
    private lateinit var bookTable: String

    @Before
    fun setUp() {
        bookTable = DBUtility.getTableNameByClassName(Book::class.java.name)
        book = Book()
        book.bookName = "数据库"
        book.pages = 300
        book.save()
    }

    @Test
    fun testQueryBySQL() {
        val cursor = LitePal.findBySQL("select * from $bookTable")!!
        assertTrue(cursor.count > 0)
        cursor.close()
    }

    @Test
    fun testQueryBySQLWithPlaceHolder() {
        val cursor = LitePal.findBySQL(
            "select * from $bookTable where id=? and bookname=? and pages=?",
            book.id.toString(),
            "数据库",
            "300"
        )!!
        assertEquals(1, cursor.count)
        cursor.moveToFirst()
        val bookName = cursor.getString(cursor.getColumnIndexOrThrow("bookname"))
        val pages = cursor.getInt(cursor.getColumnIndexOrThrow("pages"))
        assertEquals(bookName, "数据库")
        assertEquals(pages, 300)
        cursor.close()
    }

    @Test
    fun testQueryBySQLWithWrongParams() {
        try {
            LitePal.findBySQL(
                "select * from $bookTable where id=? and bookname=? and pages=?",
                book.id.toString(),
                "数据库"
            )
            fail()
        } catch (e: DataSupportException) {
            assertEquals("The parameters in conditions are incorrect.", e.message)
        }
        var cursor: Cursor? = LitePal.findBySQL()
        assertNull(cursor)
        cursor = LitePal.findBySQL()
        assertNull(cursor)
    }
}
