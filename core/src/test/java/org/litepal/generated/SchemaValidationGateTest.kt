package org.litepal.generated

import android.database.sqlite.SQLiteDatabase
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalRuntime
import org.litepal.LitePalRuntimeOptions
import org.litepal.SchemaValidationMode
import org.litepal.crud.LitePalSupport
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SchemaValidationGateTest {

    @Before
    fun setUp() {
        LitePal.initialize(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        System.clearProperty("litepal.generated.registry")
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions())
    }

    @Test
    fun validate_shouldThrowInStrictModeWhenColumnMissing() {
        val db = SQLiteDatabase.create(null)
        db.execSQL("create table if not exists User (id integer primary key autoincrement, name text)")

        System.setProperty("litepal.generated.registry", StrictValidationRegistry::class.java.name)
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions(schemaValidationMode = SchemaValidationMode.STRICT))

        var thrown: Throwable? = null
        try {
            SchemaValidationGate.validate(db)
        } catch (t: Throwable) {
            thrown = t
        } finally {
            db.close()
        }
        assertTrue("unexpected throwable=$thrown", thrown is IllegalStateException)
    }

    @Test
    fun validate_shouldNotThrowInLogModeWhenColumnMissing() {
        val db = SQLiteDatabase.create(null)
        db.execSQL("create table if not exists User (id integer primary key autoincrement, name text)")

        System.setProperty("litepal.generated.registry", StrictValidationRegistry::class.java.name)
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions(schemaValidationMode = SchemaValidationMode.LOG))

        var thrown: Throwable? = null
        try {
            SchemaValidationGate.validate(db)
        } catch (t: Throwable) {
            thrown = t
        } finally {
            db.close()
        }
        assertTrue("unexpected throwable=$thrown", thrown == null)
    }

    @Test
    fun validate_shouldThrowWhenExpectedDefaultIsMissing() {
        val db = SQLiteDatabase.create(null)
        db.execSQL("create table if not exists User (id integer primary key autoincrement, name text, age integer)")

        System.setProperty("litepal.generated.registry", DefaultRequiredRegistry::class.java.name)
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions(schemaValidationMode = SchemaValidationMode.STRICT))

        var thrown: Throwable? = null
        try {
            SchemaValidationGate.validate(db)
        } catch (t: Throwable) {
            thrown = t
        } finally {
            db.close()
        }
        assertTrue("unexpected throwable=$thrown", thrown is IllegalStateException)
    }

    @Test
    fun validate_shouldShortCircuitWhenStoredHashMatches() {
        val db = SQLiteDatabase.create(null)
        db.execSQL("create table if not exists User (id integer primary key autoincrement, name text)")
        db.execSQL(
            "create table if not exists litepal_master (" +
                "id integer primary key autoincrement, " +
                "anchor text not null unique, " +
                "schema_version integer not null, " +
                "schema_hash text not null, " +
                "updated_at integer not null)"
        )
        db.execSQL(
            "insert into litepal_master(anchor, schema_version, schema_hash, updated_at) values(?, ?, ?, ?)",
            arrayOf<Any>("test.Anchor", 1, "test-hash", System.currentTimeMillis())
        )

        System.setProperty("litepal.generated.registry", StrictValidationRegistry::class.java.name)
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions(schemaValidationMode = SchemaValidationMode.STRICT))

        var thrown: Throwable? = null
        try {
            SchemaValidationGate.validate(db)
        } catch (t: Throwable) {
            thrown = t
        } finally {
            db.close()
        }
        assertTrue("unexpected throwable=$thrown", thrown == null)
    }

    @Test
    fun validate_shouldPersistHashMarkerAfterPass() {
        val db = SQLiteDatabase.create(null)
        db.execSQL("create table if not exists User (id integer primary key autoincrement, name text, age integer not null)")

        System.setProperty("litepal.generated.registry", StrictValidationRegistry::class.java.name)
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions(schemaValidationMode = SchemaValidationMode.STRICT))

        var thrown: Throwable? = null
        var markerCount = 0
        try {
            SchemaValidationGate.validate(db)
            db.rawQuery("select count(1) from litepal_master where anchor = ?", arrayOf("test.Anchor")).use { cursor ->
                if (cursor.moveToFirst()) {
                    markerCount = cursor.getInt(0)
                }
            }
        } catch (t: Throwable) {
            thrown = t
        } finally {
            db.close()
        }
        assertTrue("unexpected throwable=$thrown", thrown == null)
        assertTrue("markerCount=$markerCount", markerCount == 1)
    }

    @Test
    fun validate_shouldUpdateExistingHashMarkerWhenRegistryChanges() {
        val db = SQLiteDatabase.create(null)
        db.execSQL("create table if not exists User (id integer primary key autoincrement, name text, age integer not null)")
        db.execSQL(
            "create table if not exists litepal_master (" +
                "id integer primary key autoincrement, " +
                "anchor text not null unique, " +
                "schema_version integer not null, " +
                "schema_hash text not null, " +
                "updated_at integer not null)"
        )
        db.execSQL(
            "insert into litepal_master(anchor, schema_version, schema_hash, updated_at) values(?, ?, ?, ?)",
            arrayOf<Any>("test.Anchor", 0, "old-hash", System.currentTimeMillis())
        )

        System.setProperty("litepal.generated.registry", StrictValidationRegistry::class.java.name)
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions(schemaValidationMode = SchemaValidationMode.STRICT))

        var thrown: Throwable? = null
        var storedVersion = -1
        var storedHash = ""
        try {
            SchemaValidationGate.validate(db)
            db.rawQuery(
                "select schema_version, schema_hash from litepal_master where anchor = ?",
                arrayOf("test.Anchor")
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    storedVersion = cursor.getInt(0)
                    storedHash = cursor.getString(1).orEmpty()
                }
            }
        } catch (t: Throwable) {
            thrown = t
        } finally {
            db.close()
        }

        assertTrue("unexpected throwable=$thrown", thrown == null)
        assertTrue("storedVersion=$storedVersion", storedVersion == 1)
        assertTrue("storedHash=$storedHash", storedHash == "test-hash")
    }

    class StrictValidationRegistry : LitePalGeneratedRegistry {
        override val schemaVersion: Int = 1
        override val schemaJson: String = "{}"
        override val schemaHash: String = "test-hash"
        override val anchorClassName: String = "test.Anchor"
        override val anchorEntities: List<String> = listOf(TestEntity::class.java.name)

        override fun entityMetasByClassName(): Map<String, EntityMeta<out LitePalSupport>> {
            val meta = GeneratedEntityMeta(
                className = TestEntity::class.java.name,
                tableName = "User",
                supportedFields = listOf("name", "age"),
                supportedGenericFields = emptyList(),
                persistedFields = listOf(
                    GeneratedFieldMeta(
                        propertyName = "name",
                        columnName = "name",
                        typeName = "java.lang.String",
                        columnType = "text",
                        nullable = true,
                        unique = false,
                        indexed = false,
                        defaultValue = "",
                        encryptAlgorithm = null
                    ),
                    GeneratedFieldMeta(
                        propertyName = "age",
                        columnName = "age",
                        typeName = "java.lang.Integer",
                        columnType = "integer",
                        nullable = false,
                        unique = false,
                        indexed = false,
                        defaultValue = "",
                        encryptAlgorithm = null
                    )
                )
            )
            return mapOf(TestEntity::class.java.name to meta)
        }
    }

    class DefaultRequiredRegistry : LitePalGeneratedRegistry {
        override val schemaVersion: Int = 1
        override val schemaJson: String = "{}"
        override val schemaHash: String = "test-hash-default"
        override val anchorClassName: String = "test.Anchor"
        override val anchorEntities: List<String> = listOf(TestEntity::class.java.name)

        override fun entityMetasByClassName(): Map<String, EntityMeta<out LitePalSupport>> {
            val meta = GeneratedEntityMeta(
                className = TestEntity::class.java.name,
                tableName = "User",
                supportedFields = listOf("name", "age"),
                supportedGenericFields = emptyList(),
                persistedFields = listOf(
                    GeneratedFieldMeta(
                        propertyName = "name",
                        columnName = "name",
                        typeName = "java.lang.String",
                        columnType = "text",
                        nullable = true,
                        unique = false,
                        indexed = false,
                        defaultValue = "",
                        encryptAlgorithm = null
                    ),
                    GeneratedFieldMeta(
                        propertyName = "age",
                        columnName = "age",
                        typeName = "java.lang.Integer",
                        columnType = "integer",
                        nullable = false,
                        unique = false,
                        indexed = false,
                        defaultValue = "18",
                        encryptAlgorithm = null
                    )
                )
            )
            return mapOf(TestEntity::class.java.name to meta)
        }
    }

    class TestEntity : LitePalSupport()
}

