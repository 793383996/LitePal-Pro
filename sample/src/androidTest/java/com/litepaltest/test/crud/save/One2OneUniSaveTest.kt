package com.litepaltest.test.crud.save

import androidx.test.filters.SmallTest
import com.litepaltest.model.IdCard
import com.litepaltest.model.Teacher
import com.litepaltest.test.LitePalTestCase
import junit.framework.TestCase.assertTrue
import org.junit.Test

@SmallTest
class One2OneUniSaveTest : LitePalTestCase() {

    private lateinit var t: Teacher
    private lateinit var i: IdCard

    private fun init() {
        t = Teacher()
        t.teacherName = "Will"
        t.teachYears = 10
        t.age = 40
        i = IdCard()
        i.number = "9997777121"
        i.address = "shanghai road"
    }

    @Test
    fun testSaveIdCardFirst() {
        init()
        t.idCard = i
        i.save()
        t.save()
        assertFK(t, i)
    }

    @Test
    fun testSaveTeacherFirst() {
        init()
        t.idCard = i
        t.save()
        i.save()
        assertFK(t, i)
    }

    @Test
    fun testBuildNullAssociations() {
        init()
        t.idCard = null
        t.save()
        i.save()
        isDataExists(getTableName(t), t.id.toLong())
        isDataExists(getTableName(i), i.id.toLong())
    }

    private fun assertFK(t: Teacher, i: IdCard) {
        assertTrue(
            isFKInsertCorrect(getTableName(t), getTableName(i), t.id.toLong(), i.id.toLong())
        )
    }
}
