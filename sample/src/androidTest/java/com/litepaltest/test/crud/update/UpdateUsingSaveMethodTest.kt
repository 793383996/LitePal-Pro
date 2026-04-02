package com.litepaltest.test.crud.update

import androidx.test.filters.SmallTest
import com.litepaltest.model.Cellphone
import com.litepaltest.model.Classroom
import com.litepaltest.model.IdCard
import com.litepaltest.model.Student
import com.litepaltest.model.Teacher
import com.litepaltest.test.LitePalTestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.litepal.LitePal
import org.litepal.util.DBUtility
import java.util.Calendar
import java.util.UUID

@SmallTest
class UpdateUsingSaveMethodTest : LitePalTestCase() {

    private lateinit var classroomTable: String
    private lateinit var studentTable: String
    private lateinit var teacherTable: String
    private lateinit var idcardTable: String

    private lateinit var c1: Classroom
    private lateinit var c2: Classroom
    private lateinit var s1: Student
    private lateinit var s2: Student
    private lateinit var s3: Student
    private lateinit var id1: IdCard
    private lateinit var t1: Teacher
    private lateinit var t2: Teacher

    @Before
    fun setUp() {
        classroomTable = DBUtility.getTableNameByClassName(Classroom::class.java.name)
        studentTable = DBUtility.getTableNameByClassName(Student::class.java.name)
        teacherTable = DBUtility.getTableNameByClassName(Teacher::class.java.name)
        idcardTable = DBUtility.getTableNameByClassName(IdCard::class.java.name)
    }

    private fun init() {
        val calendar = Calendar.getInstance()
        c1 = Classroom()
        c1.name = "Working room"
        c2 = Classroom()
        c2.name = "Resting room"
        s1 = Student()
        s1.name = "Parker"
        s1.age = 18
        s2 = Student()
        s2.name = "Peter"
        calendar.clear()
        calendar.set(1990, 9, 16, 0, 0, 0)
        s2.birthday = calendar.time
        s2.age = 19
        s3 = Student()
        s3.name = "Miley"
        s3.age = 16
        id1 = IdCard()
        id1.number = "999777123"
        id1.address = "Zhushan road"
        t1 = Teacher()
        t1.teacherName = "Jackson"
        t1.teachYears = 3
        t1.age = 28
        t2 = Teacher()
        t2.teacherName = "Rose"
        t2.teachYears = 12
        t2.age = 34
    }

    @Test
    fun testUpdateBasicValues() {
        val cell = Cellphone()
        cell.brand = "SamSung"
        cell.price = 3988.12
        cell.inStock = 'Y'
        cell.serial = UUID.randomUUID().toString()
        assertTrue(cell.save())
        assertTrue(isDataExists(getTableName(cell), cell.id ?: 0))
        cell.price = 2899.88
        cell.inStock = 'N'
        assertTrue(cell.save())
        val updatedCell = getCellPhone(cell.id ?: 0)
        assertEquals(2899.88, updatedCell?.price)
        assertEquals('N', updatedCell?.inStock)
    }

    @Test
    fun testUpdateGenericData() {
        val classroom = Classroom()
        classroom.name = "Classroom origin"
        classroom.news.add("n")
        classroom.news.add("e")
        classroom.news.add("w")
        classroom.numbers.add(1)
        classroom.numbers.add(2)
        classroom.numbers.add(3)
        classroom.save()
        classroom.name = "Classroom update"
        classroom.news.add("s")
        classroom.numbers.clear()
        classroom.save()
        val c = LitePal.find(Classroom::class.java, classroom._id.toLong())!!
        assertEquals("Classroom update", c.name)
        assertEquals(4, classroom.news.size)
        assertEquals(0, classroom.numbers.size)
        val builder = StringBuilder()
        for (s in classroom.news) {
            builder.append(s)
        }
        assertEquals("news", builder.toString())
    }

    @Test
    fun testUpdateM2OAssociationsOnMSide() {
        init()
        s1.classroom = c1
        s2.classroom = c1
        assertTrue(c1.save())
        assertTrue(c2.save())
        assertTrue(s1.save())
        assertTrue(s2.save())
        s1.classroom = c2
        s2.classroom = c2
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(1989, 7, 7, 0, 0, 0)
        s2.birthday = calendar.time
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertEquals(c2._id.toLong(), getForeignKeyValue(studentTable, classroomTable, s1.id.toLong()))
        assertEquals(c2._id.toLong(), getForeignKeyValue(studentTable, classroomTable, s2.id.toLong()))
        val student2 = LitePal.find(Student::class.java, s2.id.toLong())!!
        calendar.clear()
        calendar.set(1989, 7, 7, 0, 0, 0)
        assertEquals(calendar.timeInMillis, student2.birthday?.time)
    }

    @Test
    fun testUpdateM2OAssociationsOnOSide() {
        init()
        c1.studentCollection.add(s1)
        c1.studentCollection.add(s2)
        assertTrue(c1.save())
        assertTrue(c2.save())
        assertTrue(s1.save())
        assertTrue(s2.save())
        c2.studentCollection.add(s1)
        c2.studentCollection.add(s2)
        assertTrue(c2.save())
        assertEquals(c2._id.toLong(), getForeignKeyValue(studentTable, classroomTable, s1.id.toLong()))
        assertEquals(c2._id.toLong(), getForeignKeyValue(studentTable, classroomTable, s2.id.toLong()))
    }

    @Test
    fun testUpdateM2OAssociationsOnMSideWithNotSavedModel() {
        init()
        s1.classroom = c1
        s2.classroom = c1
        assertTrue(c1.save())
        assertTrue(s1.save())
        assertTrue(s2.save())
        s1.classroom = c2
        s2.classroom = c2
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertEquals(c1._id.toLong(), getForeignKeyValue(studentTable, classroomTable, s1.id.toLong()))
        assertEquals(c1._id.toLong(), getForeignKeyValue(studentTable, classroomTable, s2.id.toLong()))
    }

    @Test
    fun testUpdateM2OAssociationsOnOSideWithNotSavedModel() {
        init()
        c1.studentCollection.add(s1)
        c1.studentCollection.add(s2)
        assertTrue(c1.save())
        assertTrue(c2.save())
        assertTrue(s1.save())
        c2.studentCollection.add(s1)
        c2.studentCollection.add(s2)
        assertTrue(c2.save())
        assertEquals(c2._id.toLong(), getForeignKeyValue(studentTable, classroomTable, s1.id.toLong()))
    }

    @Test
    fun testUpdateM2OAssociationsOnMSideWithNull() {
        init()
        s1.classroom = c1
        s2.classroom = c1
        assertTrue(c1.save())
        assertTrue(s1.save())
        assertTrue(s2.save())
        s1.classroom = null
        s2.classroom = null
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertEquals(0, getForeignKeyValue(studentTable, classroomTable, s1.id.toLong()))
        assertEquals(0, getForeignKeyValue(studentTable, classroomTable, s2.id.toLong()))
    }

    @Test
    fun testUpdateM2OAssociationsOnOSideWithNull() {
        init()
        s1.classroom = c1
        s2.classroom = c1
        assertTrue(c1.save())
        assertTrue(s1.save())
        assertTrue(s2.save())
        setField(c1, "studentCollection", null)
        assertTrue(c1.save())
        assertEquals(0, getForeignKeyValue(studentTable, classroomTable, s1.id.toLong()))
        assertEquals(0, getForeignKeyValue(studentTable, classroomTable, s2.id.toLong()))
    }

    @Test
    fun testUpdateM2OAssociationsOnOSideWithEmptyCollection() {
        init()
        s1.classroom = c1
        s2.classroom = c1
        assertTrue(c1.save())
        assertTrue(s1.save())
        assertTrue(s2.save())
        c1.studentCollection.clear()
        assertTrue(c1.save())
        assertEquals(0, getForeignKeyValue(studentTable, classroomTable, s1.id.toLong()))
        assertEquals(0, getForeignKeyValue(studentTable, classroomTable, s2.id.toLong()))
    }

    @Test
    fun testUpdateO2OAssociations() {
        init()
        assertTrue(s3.save())
        assertTrue(id1.save())
        s3.idcard = id1
        id1.student = s3
        assertTrue(s3.save())
        assertTrue(id1.save())
        assertEquals(s3.id.toLong(), getForeignKeyValue(idcardTable, studentTable, id1.id.toLong()))
        assertEquals(id1.id.toLong(), getForeignKeyValue(studentTable, idcardTable, s3.id.toLong()))
    }

    @Test
    fun testUpdateO2OAssociationsWithNull() {
        init()
        s3.idcard = id1
        id1.student = s3
        assertTrue(s3.save())
        assertTrue(id1.save())
        s3.idcard = null
        id1.student = null
        assertTrue(s3.save())
        assertTrue(id1.save())
        assertEquals(0, getForeignKeyValue(idcardTable, studentTable, id1.id.toLong()))
        assertEquals(0, getForeignKeyValue(studentTable, idcardTable, s3.id.toLong()))
    }

    @Test
    fun testUpdateM2MAssociations() {
        init()
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertTrue(s3.save())
        assertTrue(t1.save())
        assertTrue(t2.save())
        val teachers = mutableListOf(t1, t2)
        s1.teachers = teachers
        s2.teachers = teachers
        s3.teachers = teachers
        val students = mutableListOf(s1, s2, s3)
        t1.students = students
        t2.students = students
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertTrue(s3.save())
        assertTrue(t1.save())
        assertTrue(t2.save())
        assertTrue(isIntermediateDataCorrect(getTableName(s1), getTableName(t1), s1.id.toLong(), t1.id.toLong()))
        assertTrue(isIntermediateDataCorrect(getTableName(s2), getTableName(t1), s2.id.toLong(), t1.id.toLong()))
        assertTrue(isIntermediateDataCorrect(getTableName(s3), getTableName(t1), s3.id.toLong(), t1.id.toLong()))
        assertTrue(isIntermediateDataCorrect(getTableName(s1), getTableName(t2), s1.id.toLong(), t2.id.toLong()))
        assertTrue(isIntermediateDataCorrect(getTableName(s2), getTableName(t2), s2.id.toLong(), t2.id.toLong()))
        assertTrue(isIntermediateDataCorrect(getTableName(s3), getTableName(t2), s3.id.toLong(), t2.id.toLong()))
    }

    @Test
    fun testUpdateM2MAssociationsWithNull() {
        init()
        val teachers = mutableListOf(t1, t2)
        s1.teachers = teachers
        s2.teachers = teachers
        s3.teachers = teachers
        val students = mutableListOf(s1, s2, s3)
        t1.students = students
        t2.students = students
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertTrue(s3.save())
        assertTrue(t1.save())
        assertTrue(t2.save())
        setField(s1, "teachers", null)
        setField(s2, "teachers", null)
        setField(s3, "teachers", null)
        setField(t1, "students", null)
        setField(t2, "students", null)
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertTrue(s3.save())
        assertTrue(t1.save())
        assertTrue(t2.save())
        assertFalse(isIntermediateDataCorrect(getTableName(s1), getTableName(t1), s1.id.toLong(), t1.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s2), getTableName(t1), s2.id.toLong(), t1.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s3), getTableName(t1), s3.id.toLong(), t1.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s1), getTableName(t2), s1.id.toLong(), t2.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s2), getTableName(t2), s2.id.toLong(), t2.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s3), getTableName(t2), s3.id.toLong(), t2.id.toLong()))
    }

    @Test
    fun testUpdateM2MAssociationsWithRefreshedCollection() {
        init()
        val teachers = mutableListOf(t1, t2)
        s1.teachers = teachers
        s2.teachers = teachers
        s3.teachers = teachers
        val students = mutableListOf(s1, s2, s3)
        t1.students = students
        t2.students = students
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertTrue(s3.save())
        assertTrue(t1.save())
        assertTrue(t2.save())
        teachers.clear()
        teachers.add(t2)
        students.clear()
        students.add(s3)
        s1.teachers = teachers
        s2.teachers = teachers
        s3.teachers = teachers
        t1.students = students
        t2.students = students
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertTrue(s3.save())
        assertTrue(t1.save())
        assertTrue(t2.save())
        assertFalse(isIntermediateDataCorrect(getTableName(s1), getTableName(t1), s1.id.toLong(), t1.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s2), getTableName(t1), s2.id.toLong(), t1.id.toLong()))
        assertTrue(isIntermediateDataCorrect(getTableName(s3), getTableName(t1), s3.id.toLong(), t1.id.toLong()))
        assertTrue(isIntermediateDataCorrect(getTableName(s1), getTableName(t2), s1.id.toLong(), t2.id.toLong()))
        assertTrue(isIntermediateDataCorrect(getTableName(s2), getTableName(t2), s2.id.toLong(), t2.id.toLong()))
        assertTrue(isIntermediateDataCorrect(getTableName(s3), getTableName(t2), s3.id.toLong(), t2.id.toLong()))
    }

    @Test
    fun testUpdateM2MAssociationsWithEmptyCollection() {
        init()
        val teachers = mutableListOf(t1, t2)
        s1.teachers = teachers
        s2.teachers = teachers
        s3.teachers = teachers
        val students = mutableListOf(s1, s2, s3)
        t1.students = students
        t2.students = students
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertTrue(s3.save())
        assertTrue(t1.save())
        assertTrue(t2.save())
        teachers.clear()
        students.clear()
        s1.teachers = teachers
        s2.teachers = teachers
        s3.teachers = teachers
        t1.students = students
        t2.students = students
        assertTrue(s1.save())
        assertTrue(s2.save())
        assertTrue(s3.save())
        assertTrue(t1.save())
        assertTrue(t2.save())
        assertFalse(isIntermediateDataCorrect(getTableName(s1), getTableName(t1), s1.id.toLong(), t1.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s2), getTableName(t1), s2.id.toLong(), t1.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s3), getTableName(t1), s3.id.toLong(), t1.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s1), getTableName(t2), s1.id.toLong(), t2.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s2), getTableName(t2), s2.id.toLong(), t2.id.toLong()))
        assertFalse(isIntermediateDataCorrect(getTableName(s3), getTableName(t2), s3.id.toLong(), t2.id.toLong()))
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
