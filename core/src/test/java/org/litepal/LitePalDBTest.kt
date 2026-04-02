package org.litepal

import org.junit.Assert.assertEquals
import org.junit.Test

class LitePalDBTest {

    @Test
    fun getClassNames_injects_default_table_schema_when_empty() {
        val db = LitePalDB(dbName = "demo", version = 1)

        assertEquals(listOf("org.litepal.model.Table_Schema"), db.getClassNames())
    }

    @Test
    fun setClassNames_replaces_existing_values() {
        val db = LitePalDB(dbName = "demo", version = 1)
        db.setClassNames(listOf("com.demo.Album", "com.demo.Song"))

        assertEquals(listOf("com.demo.Album", "com.demo.Song"), db.getClassNames())
    }

    @Test
    fun addClassName_appends_value() {
        val db = LitePalDB(dbName = "demo", version = 1)
        db.setClassNames(listOf("com.demo.Album"))
        db.addClassName("com.demo.Song")

        assertEquals(listOf("com.demo.Album", "com.demo.Song"), db.getClassNames())
    }

    @Test
    fun setClassNames_to_empty_restores_default() {
        val db = LitePalDB(dbName = "demo", version = 1)
        db.setClassNames(emptyList())

        assertEquals(listOf("org.litepal.model.Table_Schema"), db.getClassNames())
    }
}
