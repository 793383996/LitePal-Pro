package org.litepal.sampletest.test.crud.query

import androidx.test.filters.SmallTest
import org.litepal.sampletest.model.Student
import org.litepal.sampletest.test.LitePalTestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.litepal.LitePal
import java.util.Calendar

@SmallTest
class QueryDateTest : LitePalTestCase() {

    @Test
    fun testQueryDate() {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(1990, 9, 16, 0, 0, 0)
        val student1 = Student()
        student1.name = "Student 1"
        student1.birthday = calendar.time
        student1.save()
        val studentFromDB = LitePal.find(Student::class.java, student1.id.toLong())!!
        assertEquals("Student 1", studentFromDB.name)
        assertEquals(calendar.timeInMillis, studentFromDB.birthday!!.time)
    }

    @Test
    fun testQueryDateBefore1970() {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(1920, 6, 3, 0, 0, 0)
        val student1 = Student()
        student1.name = "Student 2"
        student1.birthday = calendar.time
        student1.save()
        val studentFromDB = LitePal.find(Student::class.java, student1.id.toLong())!!
        assertEquals("Student 2", studentFromDB.name)
        assertEquals(calendar.timeInMillis, studentFromDB.birthday!!.time)
    }

    @Test
    fun testQueryDateWithDefaultValue() {
        val student = Student()
        student.name = "School Student"
        assertTrue(student.save())
        val studentFromDB = LitePal.find(Student::class.java, student.id.toLong())!!
        assertEquals(1589203961859L, studentFromDB.schoolDate!!.time)
    }
}




