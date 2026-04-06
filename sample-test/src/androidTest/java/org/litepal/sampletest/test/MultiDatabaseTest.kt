package org.litepal.sampletest.test

import androidx.test.filters.SmallTest
import org.litepal.sampletest.model.Classroom
import org.litepal.sampletest.model.Computer
import org.litepal.sampletest.model.Headset
import org.litepal.sampletest.model.Product
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.util.DBUtility

@SmallTest
class MultiDatabaseTest : LitePalTestCase() {

    @Test
    fun testMultiDatabase() {
        LitePal.deleteDatabase("db2")
        var db = LitePal.getDatabase()
        assertTrue(DBUtility.isTableExists("Album", db))
        assertTrue(DBUtility.isTableExists("Song", db))
        assertTrue(DBUtility.isTableExists("Singer", db))
        assertTrue(DBUtility.isTableExists("Classroom", db))
        assertTrue(DBUtility.isTableExists("Teacher", db))
        assertTrue(DBUtility.isTableExists("IdCard", db))
        assertTrue(DBUtility.isTableExists("Student", db))
        assertTrue(DBUtility.isTableExists("Cellphone", db))
        assertTrue(DBUtility.isTableExists("Computer", db))
        assertTrue(DBUtility.isTableExists("Book", db))
        assertTrue(DBUtility.isTableExists("Product", db))
        assertTrue(DBUtility.isTableExists("Headset", db))
        assertTrue(DBUtility.isTableExists("WeChatMessage", db))
        assertTrue(DBUtility.isTableExists("WeiboMessage", db))

        var litePalDB = LitePalDB("db2", 1)
        litePalDB.addClassName(Classroom::class.java.name)
        litePalDB.addClassName(Product::class.java.name)
        litePalDB.isExternalStorage = true
        LitePal.use(litePalDB)
        db = LitePal.getDatabase()
        assertFalse(DBUtility.isTableExists("Album", db))
        assertFalse(DBUtility.isTableExists("Song", db))
        assertFalse(DBUtility.isTableExists("Singer", db))
        assertTrue(DBUtility.isTableExists("Classroom", db))
        assertFalse(DBUtility.isTableExists("Teacher", db))
        assertFalse(DBUtility.isTableExists("IdCard", db))
        assertFalse(DBUtility.isTableExists("Student", db))
        assertFalse(DBUtility.isTableExists("Cellphone", db))
        assertFalse(DBUtility.isTableExists("Computer", db))
        assertFalse(DBUtility.isTableExists("Book", db))
        assertTrue(DBUtility.isTableExists("Product", db))
        assertFalse(DBUtility.isTableExists("Headset", db))
        assertFalse(DBUtility.isTableExists("WeChatMessage", db))
        assertFalse(DBUtility.isTableExists("WeiboMessage", db))

        litePalDB = LitePalDB("db2", 2)
        litePalDB.addClassName(Computer::class.java.name)
        litePalDB.addClassName(Product::class.java.name)
        litePalDB.addClassName(Headset::class.java.name)
        litePalDB.isExternalStorage = true
        LitePal.use(litePalDB)
        db = LitePal.getDatabase()
        assertFalse(DBUtility.isTableExists("Album", db))
        assertFalse(DBUtility.isTableExists("Song", db))
        assertFalse(DBUtility.isTableExists("Singer", db))
        assertFalse(DBUtility.isTableExists("Classroom", db))
        assertFalse(DBUtility.isTableExists("Teacher", db))
        assertFalse(DBUtility.isTableExists("IdCard", db))
        assertFalse(DBUtility.isTableExists("Student", db))
        assertFalse(DBUtility.isTableExists("Cellphone", db))
        assertTrue(DBUtility.isTableExists("Computer", db))
        assertFalse(DBUtility.isTableExists("Book", db))
        assertTrue(DBUtility.isTableExists("Product", db))
        assertTrue(DBUtility.isTableExists("Headset", db))
        assertFalse(DBUtility.isTableExists("WeChatMessage", db))
        assertFalse(DBUtility.isTableExists("WeiboMessage", db))

        useDefaultDatabase()
        db = LitePal.getDatabase()
        assertTrue(DBUtility.isTableExists("Album", db))
        assertTrue(DBUtility.isTableExists("Song", db))
        assertTrue(DBUtility.isTableExists("Singer", db))
        assertTrue(DBUtility.isTableExists("Classroom", db))
        assertTrue(DBUtility.isTableExists("Teacher", db))
        assertTrue(DBUtility.isTableExists("IdCard", db))
        assertTrue(DBUtility.isTableExists("Student", db))
        assertTrue(DBUtility.isTableExists("Cellphone", db))
        assertTrue(DBUtility.isTableExists("Computer", db))
        assertTrue(DBUtility.isTableExists("Book", db))
        assertTrue(DBUtility.isTableExists("Product", db))
        assertTrue(DBUtility.isTableExists("Headset", db))
        assertTrue(DBUtility.isTableExists("WeChatMessage", db))
        assertTrue(DBUtility.isTableExists("WeiboMessage", db))
    }
}




