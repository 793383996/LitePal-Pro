package org.litepal.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class LitePalConfigTest {

    @Test
    fun getClassNames_injects_default_table_schema_when_empty() {
        val config = LitePalConfig()

        assertEquals(listOf("org.litepal.model.Table_Schema"), config.getClassNames())
    }

    @Test
    fun setClassNames_replaces_existing_values() {
        val config = LitePalConfig()
        config.setClassNames(listOf("com.demo.Album", "com.demo.Song"))

        assertEquals(listOf("com.demo.Album", "com.demo.Song"), config.getClassNames())
    }

    @Test
    fun addClassName_appends_value() {
        val config = LitePalConfig()
        config.setClassNames(listOf("com.demo.Album"))
        config.addClassName("com.demo.Song")

        assertEquals(listOf("com.demo.Album", "com.demo.Song"), config.getClassNames())
    }

    @Test
    fun setClassNames_to_empty_restores_default_on_read() {
        val config = LitePalConfig()
        config.setClassNames(emptyList())

        assertEquals(listOf("org.litepal.model.Table_Schema"), config.getClassNames())
    }
}
