package org.litepal

import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.crud.LitePalSupport
import org.litepal.generated.EntityMeta
import org.litepal.generated.GeneratedEntityMeta
import org.litepal.generated.GeneratedFieldMeta
import org.litepal.generated.GeneratedRegistryLocator
import org.litepal.generated.LitePalGeneratedRegistry
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OperatorSchemaValidationEpochTest {

    @Before
    fun setUp() {
        LitePal.initialize(RuntimeEnvironment.getApplication())
        GeneratedRegistryLocator.installRegistryForTesting(FailingValidationRegistry())
        LitePalRuntime.setRuntimeOptions(
            LitePalRuntimeOptions(schemaValidationMode = SchemaValidationMode.STRICT)
        )
    }

    @After
    fun tearDown() {
        GeneratedRegistryLocator.resetForTesting()
        LitePalRuntime.setRuntimeOptions(LitePalRuntimeOptions())
    }

    @Test
    fun validateSchemaIfNeeded_shouldRetryWhenValidationFailsOnSameEpoch() {
        val db = SQLiteDatabase.create(null)
        db.execSQL(
            "create table if not exists EpochUser (" +
                "id integer primary key autoincrement, " +
                "name text)"
        )

        val epoch = System.nanoTime()
        var firstError: Throwable? = null
        var secondError: Throwable? = null
        try {
            Operator.validateSchemaIfNeeded(db, epoch)
        } catch (t: Throwable) {
            firstError = t
        }
        try {
            Operator.validateSchemaIfNeeded(db, epoch)
        } catch (t: Throwable) {
            secondError = t
        } finally {
            db.close()
        }

        assertTrue("firstError=$firstError", firstError is IllegalStateException)
        assertTrue("secondError=$secondError", secondError is IllegalStateException)
    }

    private class FailingValidationRegistry : LitePalGeneratedRegistry {
        override val schemaVersion: Int = 1
        override val schemaJson: String = "{}"
        override val schemaHash: String = "operator-schema-validation-epoch-test"
        override val anchorClassName: String = "org.litepal.OperatorSchemaValidationEpochAnchor"
        override val anchorEntities: List<String> = listOf(EpochUser::class.java.name)

        override fun entityMetasByClassName(): Map<String, EntityMeta<out LitePalSupport>> {
            val meta = GeneratedEntityMeta(
                className = EpochUser::class.java.name,
                tableName = "EpochUser",
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
            return mapOf(EpochUser::class.java.name to meta)
        }
    }

    private class EpochUser : LitePalSupport()
}

