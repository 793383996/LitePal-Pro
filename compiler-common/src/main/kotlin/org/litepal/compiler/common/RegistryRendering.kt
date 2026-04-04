package org.litepal.compiler.common

import java.security.MessageDigest
import java.util.Locale

object RegistryRendering {

    fun schemaJson(model: AnchorModel): String {
        val entitiesJson = model.entities.joinToString(",") { entity ->
            val fieldsJson = entity.persistedFields.joinToString(",") { field ->
                "{\"name\":\"${field.propertyName}\",\"column\":\"${field.columnName}\",\"type\":\"${field.typeName}\",\"columnType\":\"${field.columnType}\",\"nullable\":${field.nullable},\"unique\":${field.unique},\"indexed\":${field.hasIndex},\"defaultValue\":\"${escapeJson(field.defaultValue)}\",\"encrypt\":\"${escapeJson(field.encryptAlgorithm.orEmpty())}\"}"
            }
            "{\"className\":\"${entity.className}\",\"tableName\":\"${entity.tableName}\",\"fields\":${stringArray(entity.supportedFields)},\"genericFields\":${stringArray(entity.supportedGenericFields)},\"persistedFields\":[${fieldsJson}]}"
        }
        return "{\"version\":${model.version},\"anchor\":\"${model.anchorClassName}\",\"entities\":[${entitiesJson}]}"
    }

    fun schemaHash(schemaJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(schemaJson.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun migrationReport(model: AnchorModel): String {
        return buildString {
            appendLine("LitePal schema compile report")
            appendLine("anchor=${model.anchorClassName}")
            appendLine("version=${model.version}")
            appendLine("entityCount=${model.entities.size}")
            model.entities.forEach { entity ->
                appendLine(
                    "- ${entity.className} table=${entity.tableName} fields=${entity.supportedFields.size} generic=${entity.supportedGenericFields.size} persisted=${entity.persistedFields.size}"
                )
            }
        }
    }

    fun generatedRegistrySource(model: AnchorModel): String {
        val schemaJson = schemaJson(model)
        val schemaHash = schemaHash(schemaJson)
        val generatedMappingHelpers = model.entities.mapIndexed { index, entity ->
            generateEntityMappingHelpers(index, entity)
        }.joinToString("\n\n")
        val entitiesLiteral = model.entities.mapIndexed { index, entity ->
            val persistedFieldsLiteral = entity.persistedFields.joinToString(",\n") { field ->
                """
                GeneratedFieldMeta(
                    propertyName = ${toKotlinString(field.propertyName)},
                    columnName = ${toKotlinString(field.columnName)},
                    typeName = ${toKotlinString(field.typeName)},
                    columnType = ${toKotlinString(field.columnType)},
                    nullable = ${field.nullable},
                    unique = ${field.unique},
                    indexed = ${field.hasIndex},
                    defaultValue = ${toKotlinString(field.defaultValue)},
                    encryptAlgorithm = ${field.encryptAlgorithm?.let { toKotlinString(it) } ?: "null"}
                )
                """.trimIndent()
            }
            val persistedFieldsBlock = if (persistedFieldsLiteral.isBlank()) {
                "listOf()"
            } else {
                """
                listOf(
                    $persistedFieldsLiteral
                )
                """.trimIndent()
            }
            val entityFactoryRef = if (entity.hasNoArgsConstructor) {
                "LitePalGeneratedEntityFactory$index"
            } else {
                "null"
            }
            val associationMetaRef = "LitePalGeneratedAssociationMeta$index"
            val binderRef = if (entity.persistedFields.isEmpty()) "null" else "LitePalGeneratedFieldBinder$index"
            val mapperRef = if (entity.persistedFields.isEmpty()) "null" else "LitePalGeneratedCursorMapper$index"
            """
            GeneratedEntityMeta(
                className = ${toKotlinString(entity.className)},
                tableName = ${toKotlinString(entity.tableName)},
                supportedFields = listOf(${quoted(entity.supportedFields)}),
                supportedGenericFields = listOf(${quoted(entity.supportedGenericFields)}),
                persistedFields = $persistedFieldsBlock,
                entityFactory = $entityFactoryRef,
                fieldBinder = $binderRef,
                cursorMapper = $mapperRef,
                associationMeta = $associationMetaRef
            )
            """.trimIndent()
        }.joinToString(",\n")
        val entitiesByClassLiteral = model.entities.joinToString(",\n") { entity ->
            "${toKotlinString(entity.className)} to metas.first { it.className == ${toKotlinString(entity.className)} }"
        }
        val anchorEntitiesLiteral = model.entities.joinToString(",") { toKotlinString(it.className) }

        return """
            package org.litepal.generated

            import android.database.Cursor
            import org.litepal.crud.LitePalSupport
            import org.litepal.util.BaseUtility
            import org.litepal.util.DBUtility
            import org.litepal.util.cipher.CipherUtil
            import java.lang.reflect.Field
            import java.util.Date

            private object LitePalGeneratedSkipValue

            private fun litePalResolveField(className: String, fieldName: String): Field {
                val fallbackFieldName = if (fieldName.startsWith("is") && fieldName.length > 2) {
                    val initial = fieldName.substring(2, 3).lowercase()
                    initial + fieldName.substring(3)
                } else {
                    null
                }
                var current: Class<*>? = Class.forName(className)
                while (current != null && current != Any::class.java) {
                    try {
                        val field = current.getDeclaredField(fieldName)
                        field.isAccessible = true
                        return field
                    } catch (_: NoSuchFieldException) {
                        if (!fallbackFieldName.isNullOrBlank()) {
                            try {
                                val fallbackField = current.getDeclaredField(fallbackFieldName)
                                fallbackField.isAccessible = true
                                return fallbackField
                            } catch (_: NoSuchFieldException) {
                                // ignore and continue to parent class
                            }
                        }
                        current = current.superclass
                    }
                }
                error("Unable to resolve field '${'$'}fieldName' for class '${'$'}className'.")
            }

            private fun litePalResolveColumnName(rawName: String): String {
                val normalized = if ("_id".equals(rawName, ignoreCase = true) || "id".equals(rawName, ignoreCase = true)) {
                    "id"
                } else {
                    rawName
                }
                return BaseUtility.changeCase(DBUtility.convertToValidColumnName(normalized)).orEmpty()
            }

            private fun litePalIsDate(typeName: String): Boolean {
                return typeName == "java.util.Date"
            }

            private fun litePalIsChar(typeName: String): Boolean {
                return typeName == "char" ||
                    typeName == "java.lang.Character" ||
                    typeName == "kotlin.Char"
            }

            private fun litePalIsBoolean(typeName: String): Boolean {
                return typeName == "boolean" ||
                    typeName == "java.lang.Boolean" ||
                    typeName == "kotlin.Boolean"
            }

            private fun litePalIsFloat(typeName: String): Boolean {
                return typeName == "float" ||
                    typeName == "java.lang.Float" ||
                    typeName == "kotlin.Float"
            }

            private fun litePalIsDouble(typeName: String): Boolean {
                return typeName == "double" ||
                    typeName == "java.lang.Double" ||
                    typeName == "kotlin.Double"
            }

            private fun litePalIsInt(typeName: String): Boolean {
                return typeName == "int" ||
                    typeName == "java.lang.Integer" ||
                    typeName == "kotlin.Int"
            }

            private fun litePalIsLong(typeName: String): Boolean {
                return typeName == "long" ||
                    typeName == "java.lang.Long" ||
                    typeName == "kotlin.Long"
            }

            private fun litePalIsShort(typeName: String): Boolean {
                return typeName == "short" ||
                    typeName == "java.lang.Short" ||
                    typeName == "kotlin.Short"
            }

            private fun litePalIsString(typeName: String): Boolean {
                return typeName == "java.lang.String" ||
                    typeName == "kotlin.String"
            }

            private fun litePalEncryptIfNeeded(value: Any?, algorithm: String?): Any? {
                if (value !is String) {
                    return value
                }
                if (algorithm == null || algorithm.isBlank()) {
                    return value
                }
                return when {
                    LitePalSupport.AES.equals(algorithm, ignoreCase = true) -> CipherUtil.aesEncrypt(value)
                    LitePalSupport.MD5.equals(algorithm, ignoreCase = true) -> CipherUtil.md5Encrypt(value)
                    else -> value
                }
            }

            private fun litePalDecryptIfNeeded(value: Any?, algorithm: String?): Any? {
                if (value !is String) {
                    return value
                }
                if (algorithm == null || algorithm.isBlank()) {
                    return value
                }
                return when {
                    LitePalSupport.AES.equals(algorithm, ignoreCase = true) -> CipherUtil.aesDecrypt(value)
                    else -> value
                }
            }

            private fun litePalValueForSave(rawValue: Any?, typeName: String, defaultValue: String, encryptAlgorithm: String?): Any? {
                if (litePalIsDate(typeName)) {
                    val dateValue = rawValue as? Date
                    if (dateValue != null) {
                        return dateValue.time
                    }
                    val parsedDefault = defaultValue.toLongOrNull()
                    return parsedDefault ?: Long.MAX_VALUE
                }
                if (rawValue == null) {
                    return LitePalGeneratedSkipValue
                }
                var value: Any? = rawValue
                if (litePalIsChar(typeName)) {
                    value = value.toString()
                }
                return litePalEncryptIfNeeded(value, encryptAlgorithm)
            }

            private fun litePalValueForUpdate(rawValue: Any?, typeName: String, encryptAlgorithm: String?): Any? {
                if (litePalIsDate(typeName)) {
                    val dateValue = rawValue as? Date
                    return dateValue?.time ?: Long.MAX_VALUE
                }
                if (rawValue == null) {
                    return null
                }
                var value: Any? = rawValue
                if (litePalIsChar(typeName)) {
                    value = value.toString()
                }
                return litePalEncryptIfNeeded(value, encryptAlgorithm)
            }

            private fun litePalReadCursorValue(cursor: Cursor, columnIndex: Int, typeName: String): Any? {
                return when {
                    litePalIsBoolean(typeName) -> cursor.getInt(columnIndex) == 1
                    litePalIsFloat(typeName) -> cursor.getFloat(columnIndex)
                    litePalIsDouble(typeName) -> cursor.getDouble(columnIndex)
                    litePalIsInt(typeName) -> cursor.getInt(columnIndex)
                    litePalIsLong(typeName) -> cursor.getLong(columnIndex)
                    litePalIsShort(typeName) -> cursor.getShort(columnIndex)
                    litePalIsChar(typeName) -> {
                        val value = cursor.getString(columnIndex)
                        if (value.isNullOrEmpty()) {
                            LitePalGeneratedSkipValue
                        } else {
                            value[0]
                        }
                    }
                    litePalIsDate(typeName) -> {
                        val dateValue = cursor.getLong(columnIndex)
                        if (dateValue == Long.MAX_VALUE) {
                            null
                        } else {
                            Date(dateValue)
                        }
                    }
                    else -> cursor.getString(columnIndex)
                }
            }

            $generatedMappingHelpers

            @Suppress("UNCHECKED_CAST")
            class LitePalGeneratedRegistryImpl : LitePalGeneratedRegistry {

                private val metas: List<EntityMeta<out LitePalSupport>> = listOf(
                    $entitiesLiteral
                )

                private val metasByClass: Map<String, EntityMeta<out LitePalSupport>> = mapOf(
                    $entitiesByClassLiteral
                )

                override val schemaVersion: Int = ${model.version}
                override val schemaJson: String = ${toKotlinString(schemaJson)}
                override val schemaHash: String = "$schemaHash"
                override val anchorClassName: String = ${toKotlinString(model.anchorClassName)}
                override val anchorEntities: List<String> = listOf($anchorEntitiesLiteral)

                override fun entityMetasByClassName(): Map<String, EntityMeta<out LitePalSupport>> = metasByClass
            }
        """.trimIndent()
    }

    private fun generateEntityMappingHelpers(index: Int, entity: EntityModel): String {
        val entityFactoryName = "LitePalGeneratedEntityFactory$index"
        val associationMetaName = "LitePalGeneratedAssociationMeta$index"
        val builder = StringBuilder().apply {
            if (entity.hasNoArgsConstructor) {
                appendLine("private object $entityFactoryName : EntityFactory<LitePalSupport> {")
                appendLine("    override fun newInstance(): LitePalSupport = ${entity.className}()")
                appendLine("}")
                appendLine()
            }
            appendLine("private object $associationMetaName : AssociationMeta {")
            appendLine("    override val description: String = ${toKotlinString("generated:${entity.className}")}")
            appendLine("}")
        }
        if (entity.persistedFields.isEmpty()) {
            return builder.toString().trim()
        }
        val binderName = "LitePalGeneratedFieldBinder$index"
        val mapperName = "LitePalGeneratedCursorMapper$index"
        val fieldDecls = entity.persistedFields.mapIndexed { fieldIndex, field ->
            "private val field$fieldIndex: Field = litePalResolveField(${toKotlinString(entity.className)}, ${toKotlinString(field.propertyName)})"
        }.joinToString("\n    ")

        val writableFieldEntries = entity.persistedFields.mapIndexedNotNull { fieldIndex, field ->
            if (isIdLikeName(field.columnName) || isIdLikeName(field.propertyName)) {
                null
            } else {
                fieldIndex to field
            }
        }

        val saveBlocks = writableFieldEntries.mapIndexed { writeIndex, (fieldIndex, field) ->
            """
            val saveValue$writeIndex = litePalValueForSave(
                rawValue = field$fieldIndex.get(model),
                typeName = ${toKotlinString(field.typeName)},
                defaultValue = ${toKotlinString(field.defaultValue)},
                encryptAlgorithm = ${field.encryptAlgorithm?.let { toKotlinString(it) } ?: "null"}
            )
            if (saveValue$writeIndex !== LitePalGeneratedSkipValue) {
                put(${toKotlinString(field.columnName)}, saveValue$writeIndex)
            }
            """.trimIndent()
        }.joinToString("\n\n        ").ifBlank { "/* no writable non-id fields */" }

        val updateBlocks = writableFieldEntries.mapIndexed { writeIndex, (fieldIndex, field) ->
            """
            val updateValue$writeIndex = litePalValueForUpdate(
                rawValue = field$fieldIndex.get(model),
                typeName = ${toKotlinString(field.typeName)},
                encryptAlgorithm = ${field.encryptAlgorithm?.let { toKotlinString(it) } ?: "null"}
            )
            put(${toKotlinString(field.columnName)}, updateValue$writeIndex)
            """.trimIndent()
        }.joinToString("\n\n        ").ifBlank { "/* no writable non-id fields */" }

        val mapperColumnDecls = entity.persistedFields.mapIndexed { fieldIndex, field ->
            "private val column$fieldIndex: String = litePalResolveColumnName(${toKotlinString(field.columnName)})"
        }.joinToString("\n    ")

        val mapperBlocks = entity.persistedFields.mapIndexed { fieldIndex, field ->
            """
            val columnIndex$fieldIndex = cursor.getColumnIndex(column$fieldIndex)
            if (columnIndex$fieldIndex != -1 && !cursor.isNull(columnIndex$fieldIndex)) {
                val rawValue$fieldIndex = litePalReadCursorValue(cursor, columnIndex$fieldIndex, ${toKotlinString(field.typeName)})
                if (rawValue$fieldIndex !== LitePalGeneratedSkipValue) {
                    val mappedValue$fieldIndex = if (litePalIsString(${toKotlinString(field.typeName)})) {
                        litePalDecryptIfNeeded(rawValue$fieldIndex, ${field.encryptAlgorithm?.let { toKotlinString(it) } ?: "null"})
                    } else {
                        rawValue$fieldIndex
                    }
                    field$fieldIndex.set(model, mappedValue$fieldIndex)
                }
            }
            """.trimIndent()
        }.joinToString("\n\n        ")

        val mappingCode = """
            private object $binderName : FieldBinder<LitePalSupport> {
                $fieldDecls

                override fun bindForSave(model: LitePalSupport, put: (column: String, value: Any?) -> Unit) {
                    $saveBlocks
                }

                override fun bindForUpdate(model: LitePalSupport, put: (column: String, value: Any?) -> Unit) {
                    $updateBlocks
                }
            }

            private object $mapperName : CursorMapper<LitePalSupport> {
                $fieldDecls
                $mapperColumnDecls

                override fun mapFromCursor(model: LitePalSupport, cursor: Cursor) {
                    $mapperBlocks
                }
            }
        """.trimIndent()
        builder.appendLine()
        builder.append(mappingCode)
        return builder.toString().trim()
    }

    private fun stringArray(values: List<String>): String {
        return "[" + values.joinToString(",") { "\"${escapeJson(it)}\"" } + "]"
    }

    private fun quoted(values: List<String>): String {
        return values.joinToString(",") { toKotlinString(it) }
    }

    private fun isIdLikeName(name: String): Boolean {
        return "_id".equals(name, ignoreCase = true) || "id".equals(name, ignoreCase = true)
    }

    private fun escapeJson(value: String): String {
        return buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun toKotlinString(value: String): String {
        return buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }
}
