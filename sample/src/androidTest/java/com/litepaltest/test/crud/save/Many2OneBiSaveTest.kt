package com.litepaltest.test.crud.save

import androidx.test.filters.SmallTest
import com.litepaltest.model.Classroom
import com.litepaltest.model.Student
import com.litepaltest.test.LitePalTestCase
import junit.framework.TestCase.assertTrue
import org.junit.Test

@SmallTest
class Many2OneBiSaveTest : LitePalTestCase() {

    private var c1: Classroom? = null
    private var s1: Student? = null
    private var s2: Student? = null

    private fun init() {
        c1 = Classroom()
        c1?.name = "Computer room"
        s1 = Student()
        s1?.name = "Tom"
        s2 = Student()
        s2?.name = "Lily"
    }

    @Test
    fun testCase1() {
        init()
        val ss = linkedSetOf<Student>()
        ss.add(requireNotNull(s1))
        ss.add(requireNotNull(s2))
        requireNotNull(c1).studentCollection = ss
        c1?.save()
        s1?.save()
        s2?.save()
        assertFK(c1, s1, s2)
    }

    @Test
    fun testCase2() {
        init()
        val ss = linkedSetOf<Student>()
        ss.add(requireNotNull(s1))
        ss.add(requireNotNull(s2))
        requireNotNull(c1).studentCollection = ss
        s1?.save()
        s2?.save()
        c1?.save()
        assertFK(c1, s1, s2)
    }

    @Test
    fun testCase3() {
        init()
        val ss = linkedSetOf<Student>()
        ss.add(requireNotNull(s1))
        ss.add(requireNotNull(s2))
        requireNotNull(c1).studentCollection = ss
        s2?.save()
        c1?.save()
        s1?.save()
        assertFK(c1, s1, s2)
    }

    @Test
    fun testCase4() {
        init()
        s1?.classroom = c1
        s2?.classroom = c1
        c1?.save()
        s1?.save()
        s2?.save()
        assertFK(c1, s1, s2)
    }

    @Test
    fun testCase5() {
        init()
        s1?.classroom = c1
        s2?.classroom = c1
        s1?.save()
        s2?.save()
        c1?.save()
        assertFK(c1, s1, s2)
    }

    @Test
    fun testCase6() {
        init()
        s1?.classroom = c1
        s2?.classroom = c1
        s1?.save()
        c1?.save()
        s2?.save()
        assertFK(c1, s1, s2)
    }

    @Test
    fun testCase7() {
        init()
        s1?.classroom = c1
        s2?.classroom = c1
        val ss = linkedSetOf<Student>()
        ss.add(requireNotNull(s1))
        ss.add(requireNotNull(s2))
        requireNotNull(c1).studentCollection = ss
        c1?.save()
        s1?.save()
        s2?.save()
        assertFK(c1, s1, s2)
    }

    @Test
    fun testCase8() {
        init()
        s1?.classroom = c1
        s2?.classroom = c1
        val ss = linkedSetOf<Student>()
        ss.add(requireNotNull(s1))
        ss.add(requireNotNull(s2))
        requireNotNull(c1).studentCollection = ss
        s1?.save()
        s2?.save()
        c1?.save()
        assertFK(c1, s1, s2)
    }

    @Test
    fun testCase9() {
        init()
        s1?.classroom = c1
        s2?.classroom = c1
        val ss = linkedSetOf<Student>()
        ss.add(requireNotNull(s1))
        ss.add(requireNotNull(s2))
        requireNotNull(c1).studentCollection = ss
        s1?.save()
        c1?.save()
        s2?.save()
        assertFK(c1, s1, s2)
    }

    @Test
    fun testCase10() {
        init()
        s1 = null
        s2 = null
        val ss = linkedSetOf<Student?>()
        ss.add(s1)
        ss.add(s2)
        setField(requireNotNull(c1), "studentCollection", ss)
        expectFailureSilently {
            c1?.save()
        }
        isDataExists(getTableName(requireNotNull(c1)), c1!!._id.toLong())

        init()
        c1 = null
        s1?.classroom = c1
        s2?.classroom = c1
        expectFailureSilently {
            s1?.save()
        }
        isDataExists(getTableName(requireNotNull(s1)), s1!!.id.toLong())
        expectFailureSilently {
            s2?.save()
        }
        isDataExists(getTableName(requireNotNull(s2)), s2!!.id.toLong())
    }

    private fun assertFK(c1: Classroom?, s1: Student?, s2: Student?) {
        val classroom = requireNotNull(c1)
        val student1 = requireNotNull(s1)
        val student2 = requireNotNull(s2)
        assertTrue(
            isFKInsertCorrect(
                getTableName(classroom),
                getTableName(student1),
                classroom._id.toLong(),
                student1.id.toLong()
            )
        )
        assertTrue(
            isFKInsertCorrect(
                getTableName(classroom),
                getTableName(student2),
                classroom._id.toLong(),
                student2.id.toLong()
            )
        )
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
