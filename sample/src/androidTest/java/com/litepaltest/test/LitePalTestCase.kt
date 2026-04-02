package com.litepaltest.test

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SmallTest
import com.litepaltest.model.Book
import com.litepaltest.model.Cellphone
import com.litepaltest.model.Classroom
import com.litepaltest.model.Computer
import com.litepaltest.model.IdCard
import com.litepaltest.model.Student
import com.litepaltest.model.Teacher
import org.junit.Before
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.LitePalRuntime
import org.litepal.tablemanager.Connector
import org.litepal.util.BaseUtility
import org.litepal.util.DBUtility

@SmallTest
open class LitePalTestCase {

    @Before
    fun prepareLitePalContext() {
        val targetAppContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        LitePal.initialize(targetAppContext)
        useInstrumentedDefaultDatabase()
        LitePal.getDatabase()
    }

    protected fun useDefaultDatabase() {
        useInstrumentedDefaultDatabase()
    }

    protected fun <T> expectFailureSilently(block: () -> T): T {
        return LitePalRuntime.withSilentErrorLog(block)
    }

    protected fun assertM2M(table1: String, table2: String, id1: Long, id2: Long) {
        assertTrue(isIntermediateDataCorrect(table1, table2, id1, id2))
    }

    protected fun assertM2MFalse(table1: String, table2: String, id1: Long, id2: Long) {
        assertFalse(isIntermediateDataCorrect(table1, table2, id1, id2))
    }

    protected fun isFKInsertCorrect(table1: String, table2: String, table1Id: Long, table2Id: Long): Boolean {
        val db: SQLiteDatabase = Connector.getDatabase()
        return try {
            db.query(
                table2,
                null,
                "id = ?",
                arrayOf(table2Id.toString()),
                null,
                null,
                null
            ).use { cursor ->
                cursor.moveToFirst()
                val fkId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        BaseUtility.changeCase("${table1}_id")
                    )
                )
                fkId == table1Id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    protected fun isIntermediateDataCorrect(table1: String, table2: String, table1Id: Long, table2Id: Long): Boolean {
        val db: SQLiteDatabase = Connector.getDatabase()
        var cursor: Cursor? = null
        return try {
            val where = "${table1}_id = ? and ${table2}_id = ?"
            cursor = db.query(
                DBUtility.getIntermediateTableName(table1, table2),
                null,
                where,
                arrayOf(table1Id.toString(), table2Id.toString()),
                null,
                null,
                null
            )
            cursor.count == 1
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            cursor?.close()
        }
    }

    protected fun getForeignKeyValue(tableWithFK: String, tableWithoutFK: String, id: Long): Long {
        val cursor = Connector.getDatabase().query(
            tableWithFK,
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        var foreignKeyId = 0L
        if (cursor.moveToFirst()) {
            foreignKeyId = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    BaseUtility.changeCase("${tableWithoutFK}_id")
                )
            )
        }
        cursor.close()
        return foreignKeyId
    }

    protected fun isDataExists(table: String, id: Long): Boolean {
        val db: SQLiteDatabase = Connector.getDatabase()
        return try {
            db.query(
                table,
                null,
                "id = ?",
                arrayOf(id.toString()),
                null,
                null,
                null
            ).use { cursor -> cursor.count == 1 }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    protected fun getTableName(`object`: Any): String {
        return DBUtility.getTableNameByClassName(`object`.javaClass.name)
    }

    protected fun getTableName(c: Class<*>): String {
        return DBUtility.getTableNameByClassName(c.name)
    }

    protected fun getRowsCount(tableName: String): Int {
        val c = Connector.getDatabase().query(tableName, null, null, null, null, null, null)
        val count = c.count
        c.close()
        return count
    }

    protected fun getBooks(
        columns: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        groupBy: String?,
        having: String?,
        orderBy: String?,
        limit: String?
    ): List<Book> {
        val books: MutableList<Book> = ArrayList()
        val cursor = Connector.getDatabase().query(
            getTableName(Book::class.java),
            columns,
            selection,
            selectionArgs,
            groupBy,
            having,
            orderBy,
            limit
        )
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val bookName = cursor.getString(cursor.getColumnIndexOrThrow("bookname"))
                var pages: Int? = null
                if (!cursor.isNull(cursor.getColumnIndexOrThrow("pages"))) {
                    pages = cursor.getInt(cursor.getColumnIndexOrThrow("pages"))
                }
                val price = cursor.getDouble(cursor.getColumnIndexOrThrow("price"))
                val level = cursor.getString(cursor.getColumnIndexOrThrow("level"))[0]
                val isbn = cursor.getShort(cursor.getColumnIndexOrThrow("isbn"))
                val area = cursor.getFloat(cursor.getColumnIndexOrThrow("area"))
                val isPublished = cursor.getInt(cursor.getColumnIndexOrThrow("ispublished")) == 1
                val book = Book()
                book.id = id
                book.bookName = bookName
                book.pages = pages
                book.price = price
                book.level = level
                book.isbn = isbn
                book.area = area
                book.isPublished = isPublished
                books.add(book)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return books
    }

    protected fun getClassroom(id: Long): Classroom? {
        var classroom: Classroom? = null
        val cursor = Connector.getDatabase().query(
            getTableName(Classroom::class.java),
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            classroom = Classroom()
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            classroom.name = name
        }
        cursor.close()
        return classroom
    }

    protected fun getIdCard(id: Long): IdCard? {
        var card: IdCard? = null
        val cursor = Connector.getDatabase().query(
            getTableName(IdCard::class.java),
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            card = IdCard()
            val address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
            val number = cursor.getString(cursor.getColumnIndexOrThrow("number"))
            card.address = address
            card.number = number
        }
        cursor.close()
        return card
    }

    protected fun getComputer(id: Long): Computer? {
        var computer: Computer? = null
        val cursor = Connector.getDatabase().query(
            getTableName(Computer::class.java),
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            computer = Computer("", 0.0)
            val newPrice = cursor.getDouble(cursor.getColumnIndexOrThrow("price"))
            val brand = cursor.getString(cursor.getColumnIndexOrThrow("brand"))
            computer.brand = brand
            computer.price = newPrice
        }
        cursor.close()
        return computer
    }

    protected fun getCellPhone(id: Long): Cellphone? {
        var cellPhone: Cellphone? = null
        val cursor = Connector.getDatabase().query(
            getTableName(Cellphone::class.java),
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            cellPhone = Cellphone()
            val newPrice = cursor.getDouble(cursor.getColumnIndexOrThrow("price"))
            val inStock = cursor.getString(cursor.getColumnIndexOrThrow("instock"))[0]
            val brand = cursor.getString(cursor.getColumnIndexOrThrow("brand"))
            cellPhone.brand = brand
            cellPhone.inStock = inStock
            cellPhone.price = newPrice
        }
        cursor.close()
        return cellPhone
    }

    protected fun getTeacher(id: Long): Teacher? {
        var teacher: Teacher? = null
        val cursor = Connector.getDatabase().query(
            getTableName(Teacher::class.java),
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            teacher = Teacher()
            val teacherName = cursor.getString(cursor.getColumnIndexOrThrow("teachername"))
            val teachYears = cursor.getInt(cursor.getColumnIndexOrThrow("teachyears"))
            val age = cursor.getInt(cursor.getColumnIndexOrThrow("age"))
            val sex = cursor.getInt(cursor.getColumnIndexOrThrow("sex"))
            teacher.teacherName = teacherName
            teacher.teachYears = teachYears
            teacher.age = age
            teacher.isSex = sex == 1
        }
        cursor.close()
        return teacher
    }

    protected fun getStudent(id: Long): Student? {
        var student: Student? = null
        val cursor = Connector.getDatabase().query(
            getTableName(Student::class.java),
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            student = Student()
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val age = cursor.getInt(cursor.getColumnIndexOrThrow("age"))
            student.name = name
            student.age = age
        }
        cursor.close()
        return student
    }

    protected fun getTeachers(ids: IntArray): List<Teacher> {
        val teachers: MutableList<Teacher> = ArrayList()
        val cursor = Connector.getDatabase().query(
            getTableName(Teacher::class.java),
            null,
            getWhere(ids),
            null,
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            val teacher = Teacher()
            val teacherName = cursor.getString(cursor.getColumnIndexOrThrow("teachername"))
            val teachYears = cursor.getInt(cursor.getColumnIndexOrThrow("teachyears"))
            val age = cursor.getInt(cursor.getColumnIndexOrThrow("age"))
            val sex = cursor.getInt(cursor.getColumnIndexOrThrow("sex"))
            teacher.teacherName = teacherName
            teacher.teachYears = teachYears
            teacher.age = age
            teacher.isSex = sex == 1
            teachers.add(teacher)
        }
        cursor.close()
        return teachers
    }

    protected fun getStudents(ids: IntArray): List<Student> {
        val students: MutableList<Student> = ArrayList()
        val cursor = Connector.getDatabase().query(
            getTableName(Student::class.java),
            null,
            getWhere(ids),
            null,
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            val student = Student()
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val age = cursor.getInt(cursor.getColumnIndexOrThrow("age"))
            student.name = name
            student.age = age
            students.add(student)
        }
        cursor.close()
        return students
    }

    private fun getWhere(ids: IntArray): String {
        val where = StringBuilder()
        var needOr = false
        for (id in ids) {
            if (needOr) {
                where.append(" or ")
            }
            where.append("id = ").append(id)
            needOr = true
        }
        return where.toString()
    }

    private fun useInstrumentedDefaultDatabase() {
        val litePalDB = LitePalDB(TEST_DEFAULT_DB_NAME, TEST_DEFAULT_DB_VERSION).apply {
            storage = "internal"
            setClassNames(TEST_DEFAULT_MAPPINGS)
        }
        LitePal.use(litePalDB)
    }

    companion object {
        private const val TEST_DEFAULT_DB_NAME = "sample_test"
        private const val TEST_DEFAULT_DB_VERSION = 1

        private val TEST_DEFAULT_MAPPINGS = listOf(
            "org.litepal.litepalsample.model.Album",
            "org.litepal.litepalsample.model.Song",
            "org.litepal.litepalsample.model.Singer",
            "com.litepaltest.model.Classroom",
            "com.litepaltest.model.Teacher",
            "com.litepaltest.model.IdCard",
            "com.litepaltest.model.Student",
            "com.litepaltest.model.Cellphone",
            "com.litepaltest.model.Computer",
            "com.litepaltest.model.Book",
            "com.litepaltest.model.Product",
            "com.litepaltest.model.Headset",
            "com.litepaltest.model.WeChatMessage",
            "com.litepaltest.model.WeiboMessage"
        )
    }
}
