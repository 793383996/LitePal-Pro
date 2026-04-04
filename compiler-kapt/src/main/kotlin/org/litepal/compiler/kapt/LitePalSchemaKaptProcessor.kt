package org.litepal.compiler.kapt

import org.litepal.compiler.common.AnchorModel
import org.litepal.compiler.common.EntityModel
import org.litepal.compiler.common.PersistentFieldModel
import org.litepal.compiler.common.RegistryRendering
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions()
class LitePalSchemaKaptProcessor : AbstractProcessor() {

    private lateinit var processingEnvRef: ProcessingEnvironment

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        processingEnvRef = processingEnv
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(SCHEMA_ANCHOR_FQN)
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (roundEnv.processingOver()) {
            return false
        }

        val anchorElementType = processingEnvRef.elementUtils.getTypeElement(SCHEMA_ANCHOR_FQN)
        val anchors = roundEnv.getElementsAnnotatedWith(anchorElementType)

        if (anchors.isEmpty()) {
            processingEnvRef.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "LitePal requires exactly one @LitePalSchemaAnchor. None found."
            )
            return false
        }
        if (anchors.size > 1) {
            processingEnvRef.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "LitePal requires exactly one @LitePalSchemaAnchor. Found ${anchors.size}."
            )
            return false
        }

        val anchor = anchors.first() as TypeElement
        val annotation = anchor.annotationMirrors.firstOrNull {
            (it.annotationType.asElement() as? TypeElement)?.qualifiedName?.toString() == SCHEMA_ANCHOR_FQN
        }
        if (annotation == null) {
            processingEnvRef.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Unable to resolve @LitePalSchemaAnchor annotation values.",
                anchor
            )
            return false
        }

        val version = annotation.intArg("version") ?: 1
        val entityTypeNames = annotation.classArrayArg("entities")
        if (entityTypeNames.isEmpty()) {
            processingEnvRef.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "@LitePalSchemaAnchor must declare non-empty entities.",
                anchor
            )
            return false
        }

        val entities = entityTypeNames.mapNotNull { typeName ->
            val type = processingEnvRef.elementUtils.getTypeElement(typeName)
            if (type == null) {
                processingEnvRef.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Unable to resolve entity type: $typeName",
                    anchor
                )
                null
            } else {
                toEntityModel(type)
            }
        }

        if (entities.isEmpty()) {
            processingEnvRef.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "No valid entities resolved for @LitePalSchemaAnchor.",
                anchor
            )
            return false
        }

        val model = AnchorModel(
            version = version,
            anchorClassName = anchor.qualifiedName.toString(),
            entities = entities
        )

        writeGeneratedArtifacts(model)
        return true
    }

    private fun toEntityModel(typeElement: TypeElement): EntityModel {
        val className = typeElement.qualifiedName.toString()
        val tableName = typeElement.simpleName.toString()
        val hasNoArgsConstructor = hasNoArgsConstructor(typeElement)

        val supportedFields = linkedSetOf<String>()
        val supportedGenericFields = linkedSetOf<String>()
        val persistedFields = ArrayList<PersistentFieldModel>()

        val members = processingEnvRef.elementUtils.getAllMembers(typeElement)
        members.filterIsInstance<VariableElement>().forEach { field ->
            if (field.kind != ElementKind.FIELD) {
                return@forEach
            }
            if (field.modifiers.contains(Modifier.STATIC)) {
                return@forEach
            }
            val fieldName = field.simpleName.toString()
            if (fieldName == "Companion" || fieldName.startsWith("$")) {
                return@forEach
            }
            val columnConfig = readColumnConfig(field)
            if (columnConfig.ignore) {
                return@forEach
            }
            val normalizedTypeName = normalizeTypeName(field.asType().toString())
            if (isSupportedFieldType(normalizedTypeName)) {
                supportedFields.add(fieldName)
                persistedFields.add(
                    PersistentFieldModel(
                        propertyName = fieldName,
                        columnName = resolveColumnName(fieldName),
                        typeName = normalizedTypeName,
                        columnType = mapToColumnType(normalizedTypeName),
                        nullable = columnConfig.nullable,
                        unique = columnConfig.unique,
                        hasIndex = columnConfig.indexed,
                        defaultValue = columnConfig.defaultValue,
                        encryptAlgorithm = readEncryptAlgorithm(field)
                    )
                )
            }
            if (isCollectionType(normalizedTypeName)) {
                val genericTypeName = normalizeTypeName(
                    normalizedTypeName.substringAfter("<", "").substringBeforeLast(">", "")
                )
                if (isSupportedGenericType(genericTypeName) || genericTypeName == className) {
                    supportedGenericFields.add(fieldName)
                }
            }
        }

        return EntityModel(
            className = className,
            tableName = tableName,
            supportedFields = supportedFields.toList(),
            supportedGenericFields = supportedGenericFields.toList(),
            hasNoArgsConstructor = hasNoArgsConstructor,
            persistedFields = persistedFields
        )
    }

    private fun resolveColumnName(fieldName: String): String {
        return if ("_id".equals(fieldName, ignoreCase = true) || "id".equals(fieldName, ignoreCase = true)) {
            "id"
        } else {
            fieldName
        }
    }

    private fun hasNoArgsConstructor(typeElement: TypeElement): Boolean {
        val constructors = typeElement.enclosedElements
            .filter { it.kind == ElementKind.CONSTRUCTOR }
            .mapNotNull { it as? ExecutableElement }
        if (constructors.isEmpty()) {
            return true
        }
        return constructors.any { it.parameters.isEmpty() }
    }

    private fun readColumnConfig(field: VariableElement): ColumnConfig {
        val annotation = findAnnotation(field.annotationMirrors, COLUMN_FQN) ?: return ColumnConfig()
        return ColumnConfig(
            nullable = annotation.booleanArg("nullable", true),
            unique = annotation.booleanArg("unique", false),
            defaultValue = annotation.stringArg("defaultValue", ""),
            ignore = annotation.booleanArg("ignore", false),
            indexed = annotation.booleanArg("index", false)
        )
    }

    private fun readEncryptAlgorithm(field: VariableElement): String? {
        val annotation = findAnnotation(field.annotationMirrors, ENCRYPT_FQN) ?: return null
        val algorithm = annotation.stringArg("algorithm", "")
        return algorithm.takeIf { it.isNotBlank() }
    }

    private fun findAnnotation(annotations: List<AnnotationMirror>, annotationFqn: String): AnnotationMirror? {
        return annotations.firstOrNull {
            (it.annotationType.asElement() as? TypeElement)?.qualifiedName?.toString() == annotationFqn
        }
    }

    private fun AnnotationMirror.booleanArg(name: String, defaultValue: Boolean): Boolean {
        val entry = elementValues.entries.firstOrNull { it.key.simpleName.toString() == name } ?: return defaultValue
        return entry.value.value as? Boolean ?: defaultValue
    }

    private fun AnnotationMirror.stringArg(name: String, defaultValue: String): String {
        val entry = elementValues.entries.firstOrNull { it.key.simpleName.toString() == name } ?: return defaultValue
        return entry.value.value as? String ?: defaultValue
    }

    private fun normalizeTypeName(typeName: String): String {
        return when (typeName) {
            "kotlin.Boolean" -> "java.lang.Boolean"
            "kotlin.Float" -> "java.lang.Float"
            "kotlin.Double" -> "java.lang.Double"
            "kotlin.Int" -> "java.lang.Integer"
            "kotlin.Long" -> "java.lang.Long"
            "kotlin.Short" -> "java.lang.Short"
            "kotlin.Char" -> "java.lang.Character"
            "kotlin.String" -> "java.lang.String"
            else -> typeName
        }
    }

    private fun mapToColumnType(typeName: String): String {
        return when (typeName) {
            "boolean",
            "java.lang.Boolean",
            "int",
            "java.lang.Integer",
            "long",
            "java.lang.Long",
            "short",
            "java.lang.Short",
            "java.util.Date" -> "integer"

            "float",
            "java.lang.Float",
            "double",
            "java.lang.Double" -> "real"

            "char",
            "java.lang.Character",
            "java.lang.String" -> "text"

            else -> "text"
        }
    }

    private fun writeGeneratedArtifacts(model: AnchorModel) {
        val filer = processingEnvRef.filer

        val source = RegistryRendering.generatedRegistrySource(model)
        val sourceFile = filer.createSourceFile("org.litepal.generated.LitePalGeneratedRegistryImpl")
        sourceFile.openWriter().use { it.write(source) }

        val schemaJson = RegistryRendering.schemaJson(model)
        val schemaHash = RegistryRendering.schemaHash(schemaJson)
        val migrationReport = RegistryRendering.migrationReport(model)

        filer.createResource(
            StandardLocation.CLASS_OUTPUT,
            "org.litepal.generated",
            "schema-v${model.version}.json"
        ).openWriter().use { it.write(schemaJson) }

        filer.createResource(
            StandardLocation.CLASS_OUTPUT,
            "org.litepal.generated",
            "schema-hash.txt"
        ).openWriter().use { it.write(schemaHash) }

        filer.createResource(
            StandardLocation.CLASS_OUTPUT,
            "org.litepal.generated",
            "migration-diff-report.txt"
        ).openWriter().use { it.write(migrationReport) }
    }

    private fun AnnotationMirror.intArg(name: String): Int? {
        val entry = elementValues.entries.firstOrNull { it.key.simpleName.toString() == name } ?: return null
        return entry.value.value as? Int
    }

    private fun AnnotationMirror.classArrayArg(name: String): List<String> {
        val entry = elementValues.entries.firstOrNull { it.key.simpleName.toString() == name } ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val values = entry.value.value as? List<Any?> ?: return emptyList()
        return values.mapNotNull { value ->
            val raw = value.toString()
            if (raw.endsWith(".class")) raw.removeSuffix(".class") else raw
        }
    }

    private fun isSupportedFieldType(typeName: String): Boolean {
        return typeName in setOf(
            "boolean",
            "java.lang.Boolean",
            "float",
            "java.lang.Float",
            "double",
            "java.lang.Double",
            "int",
            "java.lang.Integer",
            "long",
            "java.lang.Long",
            "short",
            "java.lang.Short",
            "char",
            "java.lang.Character",
            "java.lang.String",
            "java.util.Date"
        )
    }

    private fun isSupportedGenericType(typeName: String): Boolean {
        return typeName in setOf(
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Boolean",
            "java.lang.Character"
        )
    }

    private fun isCollectionType(typeName: String): Boolean {
        return typeName.startsWith("java.util.List<") ||
            typeName.startsWith("java.util.Set<") ||
            typeName.startsWith("kotlin.collections.List<") ||
            typeName.startsWith("kotlin.collections.Set<")
    }

    private data class ColumnConfig(
        val nullable: Boolean = true,
        val unique: Boolean = false,
        val defaultValue: String = "",
        val ignore: Boolean = false,
        val indexed: Boolean = false
    )

    companion object {
        private const val SCHEMA_ANCHOR_FQN = "org.litepal.annotation.LitePalSchemaAnchor"
        private const val COLUMN_FQN = "org.litepal.annotation.Column"
        private const val ENCRYPT_FQN = "org.litepal.annotation.Encrypt"
    }
}
