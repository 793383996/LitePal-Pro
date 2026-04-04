package org.litepal.compiler.common

import java.security.MessageDigest

object RegistryRendering {

    fun schemaJson(model: AnchorModel): String {
        val entitiesJson = model.entities.joinToString(",") { entity ->
            val fieldsJson = entity.persistedFields.joinToString(",") { field ->
                "{\"name\":\"${field.propertyName}\",\"column\":\"${field.columnName}\",\"type\":\"${field.typeName}\",\"columnType\":\"${field.columnType}\",\"nullable\":${field.nullable},\"unique\":${field.unique},\"indexed\":${field.hasIndex},\"defaultValue\":\"${escapeJson(field.defaultValue)}\",\"encrypt\":\"${escapeJson(field.encryptAlgorithm.orEmpty())}\"}"
            }
            val genericJson = entity.genericFields.joinToString(",") { generic ->
                "{\"property\":\"${generic.propertyName}\",\"elementType\":\"${generic.elementTypeName}\",\"collection\":\"${generic.collectionType}\",\"encrypt\":\"${escapeJson(generic.encryptAlgorithm.orEmpty())}\"}"
            }
            val relationJson = entity.associationFields.joinToString(",") { relation ->
                "{\"associated\":\"${relation.associatedClassName}\",\"type\":${relation.associationType},\"holdsFk\":\"${escapeJson(relation.classHoldsForeignKey.orEmpty())}\",\"selfProperty\":\"${relation.selfPropertyName}\",\"selfCollection\":\"${escapeJson(relation.selfCollectionType.orEmpty())}\",\"reverseProperty\":\"${escapeJson(relation.reversePropertyName.orEmpty())}\",\"reverseCollection\":\"${escapeJson(relation.reverseCollectionType.orEmpty())}\"}"
            }
            "{\"className\":\"${entity.className}\",\"tableName\":\"${entity.tableName}\",\"fields\":${stringArray(entity.supportedFields)},\"genericFields\":${stringArray(entity.supportedGenericFields)},\"persistedFields\":[${fieldsJson}],\"genericFieldMeta\":[${genericJson}],\"relations\":[${relationJson}]}"
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
            val genericFieldsLiteral = entity.genericFields.joinToString(",\n") { generic ->
                """
                GeneratedGenericFieldMeta(
                    propertyName = ${toKotlinString(generic.propertyName)},
                    elementTypeName = ${toKotlinString(generic.elementTypeName)},
                    collectionType = ${toKotlinString(generic.collectionType)},
                    encryptAlgorithm = ${generic.encryptAlgorithm?.let { toKotlinString(it) } ?: "null"}
                )
                """.trimIndent()
            }
            val genericFieldsBlock = if (genericFieldsLiteral.isBlank()) {
                "listOf()"
            } else {
                """
                listOf(
                    $genericFieldsLiteral
                )
                """.trimIndent()
            }
            val relationLiteral = entity.associationFields.joinToString(",\n") { relation ->
                """
                AssociationFieldMeta(
                    associatedClassName = ${toKotlinString(relation.associatedClassName)},
                    associationType = ${relation.associationType},
                    classHoldsForeignKey = ${relation.classHoldsForeignKey?.let { toKotlinString(it) } ?: "null"},
                    selfPropertyName = ${toKotlinString(relation.selfPropertyName)},
                    selfCollectionType = ${relation.selfCollectionType?.let { toKotlinString(it) } ?: "null"},
                    reversePropertyName = ${relation.reversePropertyName?.let { toKotlinString(it) } ?: "null"},
                    reverseCollectionType = ${relation.reverseCollectionType?.let { toKotlinString(it) } ?: "null"}
                )
                """.trimIndent()
            }
            val relationBlock = if (relationLiteral.isBlank()) {
                "listOf()"
            } else {
                """
                listOf(
                    $relationLiteral
                )
                """.trimIndent()
            }
            val associationMetaBlock = """
                AssociationMeta(
                    relations = $relationBlock,
                    genericFields = $genericFieldsBlock
                )
            """.trimIndent()
            val entityFactoryRef = if (entity.hasNoArgsConstructor) {
                "LitePalGeneratedEntityFactory$index"
            } else {
                "null"
            }
            val propertyAccessorRef = "LitePalGeneratedPropertyAccessor$index"
            val binderRef = if (entity.persistedFields.isEmpty()) "null" else "LitePalGeneratedFieldBinder$index"
            val mapperRef = if (entity.persistedFields.isEmpty()) "null" else "LitePalGeneratedCursorMapper$index"
            val idAccessorRef = resolveIdAccessorRef(index, entity)
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
                idAccessor = $idAccessorRef,
                associationMeta = $associationMetaBlock,
                propertyAccessor = $propertyAccessorRef
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
            import java.util.Date

            private object LitePalGeneratedSkipValue

            @Suppress("UNCHECKED_CAST")
            private fun <T> litePalUnsafeCast(value: Any?): T = value as T

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
        val propertyAccessorName = "LitePalGeneratedPropertyAccessor$index"
        val idAccessorName = "LitePalGeneratedIdAccessor$index"
        val binderName = "LitePalGeneratedFieldBinder$index"
        val mapperName = "LitePalGeneratedCursorMapper$index"
        val entityType = entity.className
        val idField = resolveIdField(entity)

        val builder = StringBuilder().apply {
            if (entity.hasNoArgsConstructor) {
                appendLine("private object $entityFactoryName : EntityFactory<LitePalSupport> {")
                appendLine("    override fun newInstance(): LitePalSupport = ${entity.className}()")
                appendLine("}")
                appendLine()
            }
            appendLine("private object $propertyAccessorName : PropertyAccessor<LitePalSupport> {")
            appendLine("    override fun get(model: LitePalSupport, propertyName: String): Any? {")
            appendLine("        val typedModel = model as $entityType")
            appendLine("        return when (propertyName) {")
            if (entity.declaredProperties.isEmpty()) {
                appendLine(
                    "            else -> throw IllegalArgumentException(" +
                        "${toKotlinString("Unknown property for ${entity.className}: ")} + propertyName" +
                        ")"
                )
            } else {
                entity.declaredProperties.forEach { property ->
                    appendLine("            ${toKotlinString(property.propertyName)} -> typedModel.${property.propertyName}")
                }
                appendLine(
                    "            else -> throw IllegalArgumentException(" +
                        "${toKotlinString("Unknown property for ${entity.className}: ")} + propertyName" +
                        ")"
                )
            }
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    override fun set(model: LitePalSupport, propertyName: String, value: Any?) {")
            appendLine("        val typedModel = model as $entityType")
            appendLine("        when (propertyName) {")
            val writableProperties = entity.declaredProperties.filter { it.writable }
            if (writableProperties.isEmpty()) {
                appendLine(
                    "            else -> throw IllegalArgumentException(" +
                        "${toKotlinString("Unknown or read-only property for ${entity.className}: ")} + propertyName" +
                        ")"
                )
            } else {
                writableProperties.forEach { property ->
                    appendLine("            ${toKotlinString(property.propertyName)} -> typedModel.${property.propertyName} = litePalUnsafeCast(value)")
                }
                appendLine(
                    "            else -> throw IllegalArgumentException(" +
                        "${toKotlinString("Unknown or read-only property for ${entity.className}: ")} + propertyName" +
                        ")"
                )
            }
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
            if (idField != null && isIdTypeSupported(idField.typeName)) {
                appendLine()
                appendLine("private object $idAccessorName : IdAccessor<LitePalSupport> {")
                appendLine("    override fun setId(model: LitePalSupport, id: Long) {")
                appendLine("        val typedModel = model as $entityType")
                appendLine("        ${renderIdAssignment("typedModel", idField)}")
                appendLine("    }")
                appendLine("}")
            }
        }
        if (entity.persistedFields.isEmpty()) {
            return builder.toString().trim()
        }

        val writableFieldEntries = entity.persistedFields.mapIndexedNotNull { fieldIndex, field ->
            if (isIdLikeName(field.columnName) || isIdLikeName(field.propertyName)) {
                null
            } else {
                fieldIndex to field
            }
        }

        val saveBlocks = writableFieldEntries.mapIndexed { writeIndex, (_, field) ->
            """
            val saveValue$writeIndex = litePalValueForSave(
                rawValue = typedModel.${field.propertyName},
                typeName = ${toKotlinString(field.typeName)},
                defaultValue = ${toKotlinString(field.defaultValue)},
                encryptAlgorithm = ${field.encryptAlgorithm?.let { toKotlinString(it) } ?: "null"}
            )
            if (saveValue$writeIndex !== LitePalGeneratedSkipValue) {
                put(${toKotlinString(field.columnName)}, saveValue$writeIndex)
            }
            """.trimIndent()
        }.joinToString("\n\n        ").ifBlank { "/* no writable non-id fields */" }

        val updateBlocks = writableFieldEntries.mapIndexed { writeIndex, (_, field) ->
            """
            val updateValue$writeIndex = litePalValueForUpdate(
                rawValue = typedModel.${field.propertyName},
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
                    ${renderMapperAssignment("typedModel", field, "mappedValue$fieldIndex")}
                }
            }
            """.trimIndent()
        }.joinToString("\n\n        ")

        val mappingCode = """
            private object $binderName : FieldBinder<LitePalSupport> {
                override fun bindForSave(model: LitePalSupport, put: (column: String, value: Any?) -> Unit) {
                    val typedModel = model as $entityType
                    $saveBlocks
                }

                override fun bindForUpdate(model: LitePalSupport, put: (column: String, value: Any?) -> Unit) {
                    val typedModel = model as $entityType
                    $updateBlocks
                }
            }

            private object $mapperName : CursorMapper<LitePalSupport> {
                $mapperColumnDecls

                override fun mapFromCursor(model: LitePalSupport, cursor: Cursor) {
                    val typedModel = model as $entityType
                    $mapperBlocks
                }
            }
        """.trimIndent()
        builder.appendLine()
        builder.append(mappingCode)
        return builder.toString().trim()
    }

    private fun resolveIdAccessorRef(index: Int, entity: EntityModel): String {
        val idField = resolveIdField(entity) ?: return "null"
        return if (isIdTypeSupported(idField.typeName)) {
            "LitePalGeneratedIdAccessor$index"
        } else {
            "null"
        }
    }

    private fun resolveIdField(entity: EntityModel): PersistentFieldModel? {
        return entity.persistedFields.firstOrNull { field ->
            isIdLikeName(field.columnName) || isIdLikeName(field.propertyName)
        }
    }

    private fun isIdTypeSupported(typeName: String): Boolean {
        return when (normalizeTypeToken(typeName)) {
            "INT", "LONG" -> true
            else -> false
        }
    }

    private fun renderIdAssignment(modelVar: String, field: PersistentFieldModel): String {
        return when (normalizeTypeToken(field.typeName)) {
            "INT" -> "$modelVar.${field.propertyName} = id.toInt()"
            else -> "$modelVar.${field.propertyName} = id"
        }
    }

    private fun renderMapperAssignment(
        modelVar: String,
        field: PersistentFieldModel,
        valueVar: String
    ): String {
        val target = "$modelVar.${field.propertyName}"
        return when (normalizeTypeToken(field.typeName)) {
            "BOOLEAN" -> "$target = when (val value = $valueVar) { is Boolean -> value; is Number -> value.toInt() == 1; is String -> value == \"1\" || value.equals(\"true\", ignoreCase = true); else -> false }"
            "FLOAT" -> "$target = (($valueVar as? Number)?.toFloat() ?: 0f)"
            "DOUBLE" -> "$target = (($valueVar as? Number)?.toDouble() ?: 0.0)"
            "INT" -> "$target = (($valueVar as? Number)?.toInt() ?: 0)"
            "LONG" -> "$target = (($valueVar as? Number)?.toLong() ?: 0L)"
            "SHORT" -> "$target = (($valueVar as? Number)?.toShort() ?: 0)"
            "CHAR" -> "$target = when (val value = $valueVar) { is Char -> value; is String -> value.firstOrNull() ?: '\\u0000'; else -> '\\u0000' }"
            "DATE" -> "if ($valueVar is Date) { $target = $valueVar }"
            "STRING" -> "$target = $valueVar.toString()"
            else -> "$target = $valueVar as ${field.typeName}"
        }
    }

    private fun normalizeTypeToken(typeName: String): String {
        return when (typeName) {
            "boolean", "java.lang.Boolean", "kotlin.Boolean" -> "BOOLEAN"
            "float", "java.lang.Float", "kotlin.Float" -> "FLOAT"
            "double", "java.lang.Double", "kotlin.Double" -> "DOUBLE"
            "int", "java.lang.Integer", "kotlin.Int" -> "INT"
            "long", "java.lang.Long", "kotlin.Long" -> "LONG"
            "short", "java.lang.Short", "kotlin.Short" -> "SHORT"
            "char", "java.lang.Character", "kotlin.Char" -> "CHAR"
            "java.lang.String", "kotlin.String" -> "STRING"
            "java.util.Date" -> "DATE"
            else -> "OTHER"
        }
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
