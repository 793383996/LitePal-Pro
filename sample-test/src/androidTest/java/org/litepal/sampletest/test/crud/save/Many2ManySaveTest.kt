package org.litepal.sampletest.test.crud.save

import androidx.test.filters.SmallTest
import org.litepal.sampletest.model.Student
import org.litepal.sampletest.model.Teacher
import org.litepal.sampletest.test.LitePalTestCase
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.litepal.crud.LitePalSupport
import java.util.Random

@SmallTest
class Many2ManySaveTest : LitePalTestCase() {

    private lateinit var danny: Student
    private lateinit var mick: Student
    private lateinit var cam: Teacher
    private lateinit var jack: Teacher

    private fun init() {
        danny = Student()
        danny.name = "Danny"
        danny.age = 14
        mick = Student()
        mick.name = "Mick"
        mick.age = 13
        cam = Teacher()
        cam.teacherName = "Cam"
        cam.age = 33
        cam.isSex = true
        cam.teachYears = 5
        jack = Teacher()
        jack.teacherName = "Jack"
        jack.age = 36
        jack.isSex = false
        jack.teachYears = 11
    }

    private fun buildBidirectionalAssociation() {
        danny.teachers.add(jack)
        danny.teachers.add(cam)
        mick.teachers.add(jack)
        mick.teachers.add(cam)
        cam.students.add(danny)
        cam.students.add(mick)
        jack.students.add(danny)
        jack.students.add(mick)
    }

    private fun buildUnidirectionalAssociation() {
        if (Math.random() >= 0.5) {
            danny.teachers.add(jack)
            danny.teachers.add(cam)
            mick.teachers.add(jack)
            mick.teachers.add(cam)
        } else {
            cam.students.add(danny)
            cam.students.add(mick)
            jack.students.add(danny)
            jack.students.add(mick)
        }
    }

    private fun getModelList(): MutableList<LitePalSupport> {
        return mutableListOf(jack, danny, cam, mick)
    }

    private fun saveAllByRandom() {
        val modelList = getModelList()
        while (modelList.isNotEmpty()) {
            val rand = Random()
            val index = rand.nextInt(modelList.size)
            val model = modelList.removeAt(index)
            model.save()
        }
    }

    @Test
    fun testCase1() {
        init()
        buildBidirectionalAssociation()
        saveAllByRandom()
        assertTrue(isDataExists(getTableName(danny), danny.id.toLong()))
        assertTrue(isDataExists(getTableName(mick), mick.id.toLong()))
        assertTrue(isDataExists(getTableName(cam), cam.id.toLong()))
        assertTrue(isDataExists(getTableName(jack), jack.id.toLong()))
        assertM2M(getTableName(danny), getTableName(cam), danny.id.toLong(), cam.id.toLong())
        assertM2M(getTableName(danny), getTableName(jack), danny.id.toLong(), jack.id.toLong())
        assertM2M(getTableName(mick), getTableName(cam), mick.id.toLong(), cam.id.toLong())
        assertM2M(getTableName(mick), getTableName(jack), mick.id.toLong(), jack.id.toLong())
    }

    @Test
    fun testCase2() {
        init()
        buildBidirectionalAssociation()
        danny.save()
        jack.save()
        cam.save()
        assertTrue(isDataExists(getTableName(danny), danny.id.toLong()))
        assertFalse(isDataExists(getTableName(mick), mick.id.toLong()))
        assertTrue(isDataExists(getTableName(cam), cam.id.toLong()))
        assertTrue(isDataExists(getTableName(jack), jack.id.toLong()))
        assertM2M(getTableName(danny), getTableName(cam), danny.id.toLong(), cam.id.toLong())
        assertM2M(getTableName(danny), getTableName(jack), danny.id.toLong(), jack.id.toLong())
        assertM2MFalse(getTableName(mick), getTableName(cam), mick.id.toLong(), cam.id.toLong())
        assertM2MFalse(getTableName(mick), getTableName(jack), mick.id.toLong(), jack.id.toLong())
    }

    @Test
    fun testCase3() {
        init()
        buildBidirectionalAssociation()
        jack.save()
        cam.save()
        assertFalse(isDataExists(getTableName(danny), danny.id.toLong()))
        assertFalse(isDataExists(getTableName(mick), mick.id.toLong()))
        assertTrue(isDataExists(getTableName(cam), cam.id.toLong()))
        assertTrue(isDataExists(getTableName(jack), jack.id.toLong()))
        assertM2MFalse(getTableName(danny), getTableName(cam), danny.id.toLong(), cam.id.toLong())
        assertM2MFalse(getTableName(danny), getTableName(jack), danny.id.toLong(), jack.id.toLong())
        assertM2MFalse(getTableName(mick), getTableName(cam), mick.id.toLong(), cam.id.toLong())
        assertM2MFalse(getTableName(mick), getTableName(jack), mick.id.toLong(), jack.id.toLong())
    }

    @Test
    fun testCase4() {
        init()
        buildUnidirectionalAssociation()
        saveAllByRandom()
        assertTrue(isDataExists(getTableName(danny), danny.id.toLong()))
        assertTrue(isDataExists(getTableName(mick), mick.id.toLong()))
        assertTrue(isDataExists(getTableName(cam), cam.id.toLong()))
        assertTrue(isDataExists(getTableName(jack), jack.id.toLong()))
        assertM2M(getTableName(danny), getTableName(cam), danny.id.toLong(), cam.id.toLong())
        assertM2M(getTableName(danny), getTableName(jack), danny.id.toLong(), jack.id.toLong())
        assertM2M(getTableName(mick), getTableName(cam), mick.id.toLong(), cam.id.toLong())
        assertM2M(getTableName(mick), getTableName(jack), mick.id.toLong(), jack.id.toLong())
    }
}




