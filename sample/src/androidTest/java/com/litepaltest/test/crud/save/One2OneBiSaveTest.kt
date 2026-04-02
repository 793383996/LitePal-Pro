package com.litepaltest.test.crud.save

import androidx.test.filters.SmallTest
import com.litepaltest.model.IdCard
import com.litepaltest.model.Student
import com.litepaltest.test.LitePalTestCase
import junit.framework.TestCase.assertTrue
import org.junit.Test

@SmallTest
class One2OneBiSaveTest : LitePalTestCase() {

    private lateinit var s: Student
    private lateinit var i: IdCard

    private fun init() {
        s = Student()
        s.name = "Jimmy"
        s.age = 18
        i = IdCard()
        i.number = "9997777112"
        i.address = "Nanjing road"
    }

    @Test
    fun testO2OBiSaveStudentFirst() {
        init()
        s.idcard = i
        i.student = s
        s.save()
        i.save()
        assertFK(s, i)
    }

    @Test
    fun testO2OBiSaveIdCardFirst() {
        init()
        s.idcard = i
        i.student = s
        i.save()
        s.save()
        assertFK(s, i)
    }

    @Test
    fun testO2OBiBuildNullAssocations() {
        init()
        s.idcard = null
        i.student = null
        i.save()
        s.save()
        isDataExists(getTableName(s), s.id.toLong())
        isDataExists(getTableName(i), i.id.toLong())
    }

    @Test
    fun testO2OBiBuildUniAssociationsSaveStudentFirst() {
        init()
        s.idcard = i
        s.save()
        i.save()
        assertFK(s, i)
    }

    @Test
    fun testO2OBiBuildUniAssociationsSaveIdCardFirst() {
        init()
        s.idcard = i
        i.save()
        s.save()
        assertFK(s, i)
    }

    private fun assertFK(s: Student, i: IdCard) {
        assertTrue(isFKInsertCorrect(getTableName(s), getTableName(i), s.id.toLong(), i.id.toLong()))
        assertTrue(isFKInsertCorrect(getTableName(i), getTableName(s), i.id.toLong(), s.id.toLong()))
    }
}
