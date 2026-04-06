package org.litepal.sampletest.test.crud.save

import androidx.test.filters.SmallTest
import org.litepal.sampletest.model.Classroom
import org.litepal.sampletest.model.Teacher
import org.litepal.sampletest.test.LitePalTestCase
import junit.framework.TestCase.assertTrue
import org.junit.Test

@SmallTest
class Many2OneUniSaveTest : LitePalTestCase() {

    private lateinit var c1: Classroom
    private var t1: Teacher? = null
    private var t2: Teacher? = null

    private fun init() {
        c1 = Classroom()
        c1.name = "Music room"
        t1 = Teacher()
        t1?.teacherName = "John"
        t1?.age = 25
        t2 = Teacher()
        t2?.teacherName = "Sean"
        t2?.age = 35
    }

    @Test
    fun testCase1() {
        init()
        c1.teachers.add(t1)
        c1.teachers.add(t2)
        c1.save()
        t1?.save()
        t2?.save()
        assertFK(c1, t1, t2)
    }

    @Test
    fun testCase2() {
        init()
        c1.teachers.add(t1)
        c1.teachers.add(t2)
        t1?.save()
        t2?.save()
        c1.save()
        assertFK(c1, t1, t2)
    }

    @Test
    fun testCase3() {
        init()
        c1.teachers.add(t1)
        c1.teachers.add(t2)
        t1?.save()
        c1.save()
        t2?.save()
        assertFK(c1, t1, t2)
    }

    @Test
    fun testCase4() {
        init()
        t1 = null
        t2 = null
        c1.teachers.add(t1)
        c1.teachers.add(t2)
        expectFailureSilently {
            c1.save()
        }
        isDataExists(getTableName(c1), c1._id.toLong())
    }

    private fun assertFK(c1: Classroom, t1: Teacher?, t2: Teacher?) {
        val teacher1 = requireNotNull(t1)
        val teacher2 = requireNotNull(t2)
        assertTrue(
            isFKInsertCorrect(
                getTableName(c1),
                getTableName(teacher1),
                c1._id.toLong(),
                teacher1.id.toLong()
            )
        )
        assertTrue(
            isFKInsertCorrect(
                getTableName(c1),
                getTableName(teacher2),
                c1._id.toLong(),
                teacher2.id.toLong()
            )
        )
    }
}




