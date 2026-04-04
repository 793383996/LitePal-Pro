package org.litepal.compiler.common

import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryRenderingTest {

    @Test
    fun generatedRegistrySource_shouldContainGeneratedBinderAndMapper() {
        val model = AnchorModel(
            version = 2,
            anchorClassName = "com.example.SchemaAnchor",
            entities = listOf(
                EntityModel(
                    className = "com.example.User",
                    tableName = "User",
                    supportedFields = listOf("name", "age"),
                    supportedGenericFields = listOf("tags"),
                    declaredProperties = listOf(
                        PropertyModel(
                            propertyName = "name",
                            sourceTypeName = "kotlin.String?",
                            writable = true,
                            normalizedTypeName = "java.lang.String"
                        ),
                        PropertyModel(
                            propertyName = "age",
                            sourceTypeName = "kotlin.Int",
                            writable = true,
                            normalizedTypeName = "java.lang.Integer"
                        ),
                        PropertyModel(
                            propertyName = "tags",
                            sourceTypeName = "kotlin.collections.MutableList<kotlin.String>",
                            writable = true,
                            normalizedTypeName = "kotlin.collections.MutableList",
                            collectionType = "LIST",
                            collectionElementTypeName = "java.lang.String"
                        )
                    ),
                    hasNoArgsConstructor = true,
                    persistedFields = listOf(
                        PersistentFieldModel(
                            propertyName = "name",
                            columnName = "name",
                            typeName = "java.lang.String",
                            columnType = "text",
                            nullable = true,
                            unique = false,
                            hasIndex = false,
                            defaultValue = "",
                            encryptAlgorithm = "AES"
                        ),
                        PersistentFieldModel(
                            propertyName = "age",
                            columnName = "age",
                            typeName = "java.lang.Integer",
                            columnType = "integer",
                            nullable = false,
                            unique = false,
                            hasIndex = true,
                            defaultValue = "0",
                            encryptAlgorithm = null
                        )
                    )
                )
            )
        )

        val source = RegistryRendering.generatedRegistrySource(model)
        val schemaJson = RegistryRendering.schemaJson(model)

        assertTrue(source.contains("LitePalGeneratedFieldBinder0"))
        assertTrue(source.contains("LitePalGeneratedCursorMapper0"))
        assertTrue(source.contains("persistedFields ="))
        assertTrue(source.contains("GeneratedFieldMeta("))
        assertTrue(schemaJson.contains("\"columnType\":\"text\""))
        assertTrue(schemaJson.contains("\"encrypt\":\"AES\""))
    }

    @Test
    fun generatedRegistrySource_shouldFallbackToNullMapperWhenNoPersistedFields() {
        val model = AnchorModel(
            version = 1,
            anchorClassName = "com.example.SchemaAnchor",
            entities = listOf(
                EntityModel(
                    className = "com.example.Empty",
                    tableName = "Empty",
                    supportedFields = emptyList(),
                    supportedGenericFields = emptyList(),
                    declaredProperties = emptyList(),
                    hasNoArgsConstructor = true,
                    persistedFields = emptyList()
                )
            )
        )

        val source = RegistryRendering.generatedRegistrySource(model)
        assertTrue(source.contains("fieldBinder = null"))
        assertTrue(source.contains("cursorMapper = null"))
    }
}
