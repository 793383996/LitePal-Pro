package org.litepal.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.tablemanager.AssociationCreator

@RunWith(AndroidJUnit4::class)
class DBUtilityInstrumentedTest {

    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LitePal.initialize(context)
        db = SQLiteDatabase.create(null)
        createSchema()
    }

    @After
    fun tearDown() {
        if (db.isOpen) {
            db.close()
        }
    }

    @Test
    fun convert_column_related_clauses_keeps_sql_valid_for_keywords() {
        assertEquals("select_lpcolumn", DBUtility.convertToValidColumnName("select"))
        assertEquals("name", DBUtility.convertToValidColumnName("name"))

        val where = DBUtility.convertWhereClauseToColumnName(
            "select = ? and name like ? and from in (?, ?) and order between ? and ?"
        )
        assertEquals(
            "select_lpcolumn = ? and name like ? and from_lpcolumn in (?, ?) and order_lpcolumn between ? and ?",
            where
        )

        val select = DBUtility.convertSelectClauseToValidNames(arrayOf("name", "select", "from"))
        assertArrayEquals(arrayOf("name", "select_lpcolumn", "from_lpcolumn"), select)

        val orderBy = DBUtility.convertOrderByClauseToValidName("name desc, select asc, from")
        assertEquals("name desc,select_lpcolumn asc,from_lpcolumn", orderBy)
    }

    @Test
    fun table_and_column_existence_checks_work() {
        val tableNames = DBUtility.findAllTableNames(db)
        assertTrue(tableNames.contains("books"))
        assertTrue(DBUtility.isTableExists("books", db))
        assertTrue(DBUtility.isColumnExists("name", "books", db))
        assertTrue(DBUtility.isColumnExists("NAME", "books", db))
        assertFalse(DBUtility.isColumnExists("missing_column", "books", db))
    }

    @Test
    fun findIndexedColumns_distinguishes_normal_and_unique_indexes() {
        val indexPair = DBUtility.findIndexedColumns("books", db)
        val indexColumns = indexPair.first
        val uniqueColumns = indexPair.second

        assertTrue(indexColumns.contains("name"))
        assertTrue(uniqueColumns.contains("code"))
        assertFalse(indexColumns.contains("category"))
        assertFalse(uniqueColumns.contains("price"))
    }

    @Test
    fun findPragmaTableInfo_reads_column_metadata_correctly() {
        val tableModel = DBUtility.findPragmaTableInfo("books", db)
        assertEquals("books", tableModel.getTableName())

        val nameColumn = tableModel.getColumnModelByName("name")
        assertNotNull(nameColumn)
        assertEquals("text", nameColumn!!.getColumnType()?.lowercase())
        assertFalse(nameColumn.isNullable())
        assertTrue(nameColumn.hasIndex())
        assertEquals("'unknown'", nameColumn.getDefaultValue())

        val codeColumn = tableModel.getColumnModelByName("code")
        assertNotNull(codeColumn)
        assertTrue(codeColumn!!.isUnique())
        assertFalse(codeColumn.hasIndex())

        val priceColumn = tableModel.getColumnModelByName("price")
        assertNotNull(priceColumn)
        assertEquals("0.0", priceColumn!!.getDefaultValue())
        assertFalse(priceColumn.hasIndex())
        assertFalse(priceColumn.isUnique())
    }

    @Test
    fun tableSnapshotSession_uses_cached_table_type_and_supports_incremental_type_updates() {
        DBUtility.beginTableSnapshotSession(db)
        try {
            db.execSQL("create table genre_book (id integer primary key, name text)")
            DBUtility.onSchemaSqlExecuted(db, "create table genre_book (id integer primary key, name text)")
            assertFalse(DBUtility.isIntermediateTable("genre_book", db))

            DBUtility.noteTableSchemaType(db, "genre_book", Const.TableSchema.INTERMEDIATE_JOIN_TABLE)
            assertTrue(DBUtility.isIntermediateTable("genre_book", db))
            assertFalse(DBUtility.isGenericTable("genre_book", db))

            DBUtility.noteTableSchemaTypeRemoved(db, "genre_book")
            assertFalse(DBUtility.isIntermediateTable("genre_book", db))
        } finally {
            DBUtility.endTableSnapshotSession(db)
        }
    }

    @Test
    fun tableSnapshotSession_supports_incremental_create_drop_rename() {
        DBUtility.beginTableSnapshotSession(db)
        try {
            assertTrue(DBUtility.isTableExists("books", db))

            db.execSQL("create table albums (id integer primary key, name text)")
            DBUtility.onSchemaSqlExecuted(db, "create table albums (id integer primary key, name text)")
            assertTrue(DBUtility.isTableExists("albums", db))

            db.execSQL("alter table albums rename to albums_temp")
            DBUtility.onSchemaSqlExecuted(db, "alter table albums rename to albums_temp")
            assertFalse(DBUtility.isTableExists("albums", db))
            assertTrue(DBUtility.isTableExists("albums_temp", db))

            db.execSQL("drop table albums_temp")
            DBUtility.onSchemaSqlExecuted(db, "drop table albums_temp")
            assertFalse(DBUtility.isTableExists("albums_temp", db))
        } finally {
            DBUtility.endTableSnapshotSession(db)
        }
    }

    @Test
    fun tableSnapshotSession_returns_cached_table_list_for_same_session() {
        DBUtility.beginTableSnapshotSession(db)
        try {
            val first = DBUtility.findAllTableNames(db)
            val second = DBUtility.findAllTableNames(db)
            assertEquals(first.sorted(), second.sorted())
            assertTrue(first.contains("books"))
        } finally {
            DBUtility.endTableSnapshotSession(db)
        }
    }

    @Test
    fun tableTypeDetection_supports_multiUnderscore_names() {
        db.execSQL("create table alpha_beta_gamma (id integer primary key, value text)")
        db.execSQL(
            "create table if not exists ${Const.TableSchema.TABLE_NAME} (" +
                "id integer primary key autoincrement, " +
                "${Const.TableSchema.COLUMN_NAME} text, " +
                "${Const.TableSchema.COLUMN_TYPE} integer)"
        )
        db.execSQL(
            "insert into ${Const.TableSchema.TABLE_NAME} " +
                "(${Const.TableSchema.COLUMN_NAME}, ${Const.TableSchema.COLUMN_TYPE}) values (?, ?)",
            arrayOf<Any>("alpha_beta_gamma", Const.TableSchema.INTERMEDIATE_JOIN_TABLE)
        )

        assertTrue(DBUtility.isIntermediateTable("alpha_beta_gamma", db))
        assertFalse(DBUtility.isGenericTable("alpha_beta_gamma", db))
    }

    @Test
    fun giveTableSchemaCopy_shouldAvoidFullScanDuplicatesWithinSnapshotSession() {
        db.execSQL(
            "create table if not exists ${Const.TableSchema.TABLE_NAME} (" +
                "id integer primary key autoincrement, " +
                "${Const.TableSchema.COLUMN_NAME} text, " +
                "${Const.TableSchema.COLUMN_TYPE} integer)"
        )
        DBUtility.beginTableSnapshotSession(db)
        try {
            val creator = TestAssociationCreator()
            creator.copyTableSchema("schema_cache_target", Const.TableSchema.NORMAL_TABLE, db)
            creator.copyTableSchema("schema_cache_target", Const.TableSchema.NORMAL_TABLE, db)

            assertTrue(DBUtility.hasTableSchemaEntry("schema_cache_target", db))
            var cursor: android.database.Cursor? = null
            val count = try {
                cursor = db.rawQuery(
                    "select count(1) from ${Const.TableSchema.TABLE_NAME} where lower(${Const.TableSchema.COLUMN_NAME}) = lower(?)",
                    arrayOf("schema_cache_target")
                )
                if (cursor.moveToFirst()) {
                    cursor.getInt(0)
                } else {
                    0
                }
            } finally {
                cursor?.close()
            }
            assertEquals(1, count)
        } finally {
            DBUtility.endTableSnapshotSession(db)
        }
    }

    private fun createSchema() {
        db.execSQL(
            "create table books (" +
                "id integer primary key, " +
                "name text not null default 'unknown', " +
                "price real default 0.0, " +
                "code text, " +
                "category text, " +
                "edition integer" +
                ")"
        )
        db.execSQL("create index idx_books_name on books(name)")
        db.execSQL("create unique index uk_books_code on books(code)")
        db.execSQL("create index idx_books_category_edition on books(category, edition)")
        db.execSQL("create unique index uk_books_price_edition on books(price, edition)")
    }

    private class TestAssociationCreator : AssociationCreator() {
        override fun createOrUpgradeTable(db: SQLiteDatabase, force: Boolean) = Unit

        fun copyTableSchema(tableName: String, tableType: Int, db: SQLiteDatabase) {
            giveTableSchemaACopy(tableName, tableType, db)
        }
    }
}
