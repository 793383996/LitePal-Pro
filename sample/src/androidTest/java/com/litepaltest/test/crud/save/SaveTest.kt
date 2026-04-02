package com.litepaltest.test.crud.save

import androidx.test.filters.SmallTest
import com.litepaltest.model.Book
import com.litepaltest.model.Cellphone
import com.litepaltest.model.Classroom
import com.litepaltest.model.Computer
import com.litepaltest.model.IdCard
import com.litepaltest.model.Product
import com.litepaltest.model.Student
import com.litepaltest.model.Teacher
import com.litepaltest.model.WeChatMessage
import com.litepaltest.model.WeiboMessage
import com.litepaltest.test.LitePalTestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.litepal.LitePal
import java.util.UUID

@SmallTest
class SaveTest : LitePalTestCase() {

    @Test
    fun testSave() {
        val cell = Cellphone()
        cell.brand = "iPhone"
        cell.price = 4998.01
        cell.inStock = 'Y'
        cell.serial = UUID.randomUUID().toString()
        assertTrue(cell.save())
        assertTrue(isDataExists(getTableName(cell), cell.id ?: 0))
    }

    @Test
    fun testSaveWithConstructors() {
        val computer = Computer("asus", 699.00)
        assertTrue(computer.save())
        assertTrue(isDataExists(getTableName(computer), computer.id))
        val c = getComputer(computer.id)
        assertEquals("asus", c?.brand)
        assertEquals(699.00, c?.price)
        val cc = LitePal.find(Computer::class.java, computer.id)!!
        assertEquals("asus", cc.brand)
        assertEquals(699.00, cc.price)
        val p = Product(null)
        p.brand = "apple"
        p.price = 1222.33
        p.save()
    }

    @Test
    fun testSaveAfterDelete() {
        val cell = Cellphone()
        cell.brand = "iPhone"
        cell.price = 4998.01
        cell.inStock = 'Y'
        cell.serial = UUID.randomUUID().toString()
        assertTrue(cell.save())
        assertTrue(isDataExists(getTableName(cell), cell.id ?: 0))
        assertTrue(cell.delete() > 0)
        assertTrue(cell.save())
        assertTrue(isDataExists(getTableName(cell), cell.id ?: 0))

        val stu = Student()
        stu.name = "Jimmy"
        val idcard = IdCard()
        idcard.address = "Washington"
        idcard.number = "123456"
        idcard.student = stu
        stu.idcard = idcard
        stu.save()
        idcard.save()
        assertTrue(isDataExists(getTableName(stu), stu.id.toLong()))
        assertTrue(isDataExists(getTableName(idcard), idcard.id.toLong()))
        stu.delete()
        assertFalse(isDataExists(getTableName(stu), stu.id.toLong()))
        assertFalse(isDataExists(getTableName(idcard), idcard.id.toLong()))
        stu.save()
        idcard.save()
        assertTrue(isDataExists(getTableName(stu), stu.id.toLong()))
        assertTrue(isDataExists(getTableName(idcard), idcard.id.toLong()))

        val danny = Student()
        danny.name = "Danny"
        danny.age = 14
        val cam = Teacher()
        cam.teacherName = "Cam"
        cam.age = 33
        cam.isSex = true
        cam.teachYears = 5
        val jack = Teacher()
        jack.teacherName = "Jack"
        jack.age = 36
        jack.isSex = false
        jack.teachYears = 11
        danny.teachers.add(jack)
        danny.teachers.add(cam)
        cam.students.add(danny)
        jack.students.add(danny)
        danny.save()
        cam.save()
        jack.save()
        assertTrue(isDataExists(getTableName(danny), danny.id.toLong()))
        assertTrue(isDataExists(getTableName(cam), cam.id.toLong()))
        assertTrue(isDataExists(getTableName(jack), jack.id.toLong()))
        danny.delete()
        assertFalse(isDataExists(getTableName(danny), danny.id.toLong()))
        assertTrue(isDataExists(getTableName(cam), cam.id.toLong()))
        assertTrue(isDataExists(getTableName(jack), jack.id.toLong()))
        danny.save()
        assertTrue(isDataExists(getTableName(danny), danny.id.toLong()))
        assertEquals(danny.teachers.size, 2)

        val classroom = Classroom()
        classroom.name = "test classroom"
        val s = Student()
        s.name = "Tom"
        s.classroom = classroom
        val s2 = Student()
        s2.name = "Tom"
        s2.classroom = classroom
        assertTrue(classroom.save())
        assertTrue(s.save())
        assertTrue(s2.save())
        assertTrue(isDataExists(getTableName(classroom), classroom._id.toLong()))
        assertTrue(isDataExists(getTableName(s), s.id.toLong()))
        assertTrue(isDataExists(getTableName(s), s2.id.toLong()))
        classroom.delete()
        assertFalse(isDataExists(getTableName(classroom), classroom._id.toLong()))
        assertFalse(isDataExists(getTableName(s), s.id.toLong()))
        assertFalse(isDataExists(getTableName(s), s2.id.toLong()))
        classroom.save()
        s.save()
        s2.save()
        assertTrue(isDataExists(getTableName(classroom), classroom._id.toLong()))
        assertTrue(isDataExists(getTableName(s), s.id.toLong()))
        assertTrue(isDataExists(getTableName(s), s2.id.toLong()))
    }

    @Test
    fun testSaveInheritModels() {
        val weChatMessage = WeChatMessage()
        weChatMessage.friend = "Tom"
        weChatMessage.content = "Hello nice to meet you"
        weChatMessage.title = "Greeting message"
        weChatMessage.type = 1
        assertTrue(weChatMessage.save())
        assertTrue(weChatMessage.id > 0)
        val message1 = LitePal.find(WeChatMessage::class.java, weChatMessage.id.toLong())!!
        assertEquals("Tom", message1.friend)
        assertEquals("Hello nice to meet you", message1.content)
        assertNull(message1.title)
        assertEquals(1, message1.type)

        val weiboMessage = WeiboMessage()
        weiboMessage.type = 2
        weiboMessage.title = "Following message"
        weiboMessage.content = "Something big happens"
        weiboMessage.follower = "Jimmy"
        weiboMessage.number = 123456
        assertTrue(weiboMessage.save())
        assertTrue(weiboMessage.id > 0)
    }

    @Test
    fun testSaveInheritModelsWithAssociations() {
        val cellphone = Cellphone()
        cellphone.brand = "iPhone 7"
        cellphone.inStock = 'N'
        cellphone.price = 6999.99
        cellphone.serial = UUID.randomUUID().toString()
        cellphone.mac = "ff:3d:4a:99:76"
        cellphone.save()

        val weChatMessage = WeChatMessage()
        weChatMessage.friend = "Tom"
        weChatMessage.content = "Hello nice to meet you"
        weChatMessage.title = "Greeting message"
        weChatMessage.type = 1
        assertTrue(weChatMessage.save())
        assertTrue(weChatMessage.id > 0)
        val message1 = LitePal.find(WeChatMessage::class.java, weChatMessage.id.toLong())!!
        assertEquals("Tom", message1.friend)
        assertEquals("Hello nice to meet you", message1.content)
        assertNull(message1.title)
        assertEquals(1, message1.type)

        val weiboMessage = WeiboMessage()
        weiboMessage.type = 2
        weiboMessage.title = "Following message"
        weiboMessage.content = "Something big happens"
        weiboMessage.follower = "Jimmy"
        weiboMessage.number = 123456
        weiboMessage.cellphone = cellphone
        assertTrue(weiboMessage.save())
        assertTrue(weiboMessage.id > 0)
        val message2 = LitePal.find(WeiboMessage::class.java, weiboMessage.id.toLong(), true)!!
        val result = message2.cellphone
        assertEquals(cellphone.id, result?.id)
        assertEquals(cellphone.brand, result?.brand)
        assertEquals(cellphone.inStock, result?.inStock)
        assertEquals(cellphone.price, result?.price)
        assertEquals(cellphone.serial, result?.serial)
        assertEquals(cellphone.mac, result?.mac)
    }

    @Test
    fun testSaveGenericData() {
        val classroom = Classroom()
        classroom.name = "classroom1"
        classroom.news.add("news1")
        classroom.news.add("news2")
        classroom.news.add("news3")
        val numbers = mutableListOf(1, 2, 3, 4)
        classroom.numbers = numbers
        classroom.save()
        val c = LitePal.find(Classroom::class.java, classroom._id.toLong())!!
        assertEquals("classroom1", c.name)
        assertEquals(3, c.news.size)
        assertEquals(4, c.numbers.size)
        for (news in c.news) {
            assertTrue(news == "news1" || news == "news2" || news == "news3")
        }
        for (number in c.numbers) {
            assertTrue(number == 1 || number == 2 || number == 3 || number == 4)
        }
    }

    @Test
    fun testSaveLongMaximumNumber() {
        val idCard = IdCard()
        idCard.serial = Long.MAX_VALUE
        idCard.address = "abczyx"
        assertTrue(idCard.save())
        val idCardFromDB = LitePal.find(IdCard::class.java, idCard.id.toLong())!!
        assertEquals(Long.MAX_VALUE, idCardFromDB.serial)
    }

    @Test
    fun testNullValue() {
        val book = Book()
        book.bookName = "First Line of Android"
        assertTrue(book.save())
        var bookFromDB = LitePal.find(Book::class.java, book.id)
        assertNotNull(bookFromDB)
        assertNull(bookFromDB!!.pages)

        book.pages = 123
        assertTrue(book.save())
        bookFromDB = LitePal.find(Book::class.java, book.id)
        assertNotNull(bookFromDB)
        assertNotNull(bookFromDB!!.pages)
        assertEquals(Integer.valueOf(123), book.pages)
    }
}
