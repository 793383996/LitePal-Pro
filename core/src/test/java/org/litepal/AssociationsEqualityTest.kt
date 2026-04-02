package org.litepal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.litepal.crud.model.AssociationsInfo
import org.litepal.tablemanager.model.AssociationsModel
import org.litepal.util.Const

class AssociationsEqualityTest {

    @Test
    fun associationsModel_equals_is_symmetric_when_tables_are_swapped() {
        val left = createAssociationsModel(
            tableName = "book",
            associatedTableName = "author",
            tableHoldsForeignKey = "book",
            associationType = Const.Model.MANY_TO_ONE
        )
        val right = createAssociationsModel(
            tableName = "author",
            associatedTableName = "book",
            tableHoldsForeignKey = "book",
            associationType = Const.Model.MANY_TO_ONE
        )

        assertTrue(left == right)
        assertTrue(right == left)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun associationsModel_not_equals_when_fk_holder_or_type_differs() {
        val baseline = createAssociationsModel(
            tableName = "book",
            associatedTableName = "author",
            tableHoldsForeignKey = "book",
            associationType = Const.Model.MANY_TO_ONE
        )
        val differentFkHolder = createAssociationsModel(
            tableName = "author",
            associatedTableName = "book",
            tableHoldsForeignKey = "author",
            associationType = Const.Model.MANY_TO_ONE
        )
        val differentType = createAssociationsModel(
            tableName = "author",
            associatedTableName = "book",
            tableHoldsForeignKey = "book",
            associationType = Const.Model.ONE_TO_ONE
        )

        assertFalse(baseline == differentFkHolder)
        assertFalse(baseline == differentType)
    }

    @Test
    fun associationsModel_not_equals_when_critical_fields_are_null() {
        val baseline = createAssociationsModel(
            tableName = "book",
            associatedTableName = "author",
            tableHoldsForeignKey = "book",
            associationType = Const.Model.MANY_TO_ONE
        )
        val missingTableName = createAssociationsModel(
            tableName = null,
            associatedTableName = "author",
            tableHoldsForeignKey = "book",
            associationType = Const.Model.MANY_TO_ONE
        )

        assertFalse(baseline == missingTableName)
    }

    @Test
    fun associationsInfo_equals_is_symmetric_when_classes_are_swapped() {
        val left = createAssociationsInfo(
            selfClassName = "org.demo.Book",
            associatedClassName = "org.demo.Author",
            classHoldsForeignKey = "org.demo.Book",
            associationType = Const.Model.MANY_TO_ONE
        )
        val right = createAssociationsInfo(
            selfClassName = "org.demo.Author",
            associatedClassName = "org.demo.Book",
            classHoldsForeignKey = "org.demo.Book",
            associationType = Const.Model.MANY_TO_ONE
        )

        assertTrue(left == right)
        assertTrue(right == left)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun associationsInfo_not_equals_when_fk_holder_or_type_differs() {
        val baseline = createAssociationsInfo(
            selfClassName = "org.demo.Book",
            associatedClassName = "org.demo.Author",
            classHoldsForeignKey = "org.demo.Book",
            associationType = Const.Model.MANY_TO_ONE
        )
        val differentFkHolder = createAssociationsInfo(
            selfClassName = "org.demo.Author",
            associatedClassName = "org.demo.Book",
            classHoldsForeignKey = "org.demo.Author",
            associationType = Const.Model.MANY_TO_ONE
        )
        val differentType = createAssociationsInfo(
            selfClassName = "org.demo.Author",
            associatedClassName = "org.demo.Book",
            classHoldsForeignKey = "org.demo.Book",
            associationType = Const.Model.ONE_TO_ONE
        )

        assertFalse(baseline == differentFkHolder)
        assertFalse(baseline == differentType)
    }

    @Test
    fun associationsInfo_not_equals_when_critical_fields_are_null() {
        val baseline = createAssociationsInfo(
            selfClassName = "org.demo.Book",
            associatedClassName = "org.demo.Author",
            classHoldsForeignKey = "org.demo.Book",
            associationType = Const.Model.MANY_TO_ONE
        )
        val missingSelfClass = createAssociationsInfo(
            selfClassName = null,
            associatedClassName = "org.demo.Author",
            classHoldsForeignKey = "org.demo.Book",
            associationType = Const.Model.MANY_TO_ONE
        )

        assertFalse(baseline == missingSelfClass)
    }

    private fun createAssociationsModel(
        tableName: String?,
        associatedTableName: String?,
        tableHoldsForeignKey: String?,
        associationType: Int
    ): AssociationsModel {
        return AssociationsModel().apply {
            setTableName(tableName)
            setAssociatedTableName(associatedTableName)
            setTableHoldsForeignKey(tableHoldsForeignKey)
            setAssociationType(associationType)
        }
    }

    private fun createAssociationsInfo(
        selfClassName: String?,
        associatedClassName: String?,
        classHoldsForeignKey: String?,
        associationType: Int
    ): AssociationsInfo {
        return AssociationsInfo().apply {
            setSelfClassName(selfClassName)
            setAssociatedClassName(associatedClassName)
            setClassHoldsForeignKey(classHoldsForeignKey)
            setAssociationType(associationType)
        }
    }
}
