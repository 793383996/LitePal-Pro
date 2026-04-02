package org.litepal.tablemanager.typechange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypeChangeOrmTest {

    @Test
    fun booleanOrm_maps_supported_types_to_integer() {
        val orm = BooleanOrm()

        assertEquals("integer", orm.object2Relation("boolean"))
        assertEquals("integer", orm.object2Relation("java.lang.Boolean"))
    }

    @Test
    fun numericOrm_maps_supported_types_to_integer() {
        val orm = NumericOrm()

        assertEquals("integer", orm.object2Relation("int"))
        assertEquals("integer", orm.object2Relation("java.lang.Integer"))
        assertEquals("integer", orm.object2Relation("long"))
        assertEquals("integer", orm.object2Relation("java.lang.Long"))
        assertEquals("integer", orm.object2Relation("short"))
        assertEquals("integer", orm.object2Relation("java.lang.Short"))
    }

    @Test
    fun decimalOrm_maps_supported_types_to_real() {
        val orm = DecimalOrm()

        assertEquals("real", orm.object2Relation("float"))
        assertEquals("real", orm.object2Relation("java.lang.Float"))
        assertEquals("real", orm.object2Relation("double"))
        assertEquals("real", orm.object2Relation("java.lang.Double"))
    }

    @Test
    fun textOrm_maps_supported_types_to_text() {
        val orm = TextOrm()

        assertEquals("text", orm.object2Relation("char"))
        assertEquals("text", orm.object2Relation("java.lang.Character"))
        assertEquals("text", orm.object2Relation("java.lang.String"))
    }

    @Test
    fun dateOrm_maps_supported_type_to_integer() {
        val orm = DateOrm()

        assertEquals("integer", orm.object2Relation("java.util.Date"))
    }

    @Test
    fun blobOrm_maps_supported_type_to_blob() {
        val orm = BlobOrm()

        assertEquals("blob", orm.object2Relation("[B"))
    }

    @Test
    fun all_orms_return_null_for_unsupported_types() {
        assertNull(BooleanOrm().object2Relation("java.lang.String"))
        assertNull(NumericOrm().object2Relation("java.math.BigDecimal"))
        assertNull(DecimalOrm().object2Relation("java.lang.Integer"))
        assertNull(TextOrm().object2Relation("java.util.Date"))
        assertNull(DateOrm().object2Relation("long"))
        assertNull(BlobOrm().object2Relation("byte[]"))
    }
}
