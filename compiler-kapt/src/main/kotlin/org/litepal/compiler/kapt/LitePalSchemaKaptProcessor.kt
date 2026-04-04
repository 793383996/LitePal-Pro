package org.litepal.compiler.kapt

import org.litepal.compiler.common.AnchorModel
import org.litepal.compiler.common.EntityModel
import org.litepal.compiler.common.PersistentFieldModel
import org.litepal.compiler.common.PropertyModel
import org.litepal.compiler.common.RelationshipModeling
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
import java.io.File

@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions()
class LitePalSchemaKaptProcessor : AbstractProcessor() {

    private lateinit var processingEnvRef: ProcessingEnvironment
    private var hasCompilationError = false

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

        val rawEntities = entityTypeNames.mapNotNull { typeName ->
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
        val entities = RelationshipModeling.enrichEntities(rawEntities)

        if (entities.isEmpty()) {
            processingEnvRef.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "No valid entities resolved for @LitePalSchemaAnchor.",
                anchor
            )
            return false
        }
        if (hasCompilationError) {
            return true
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
        val declaredProperties = ArrayList<PropertyModel>()
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
            val ownerName = (field.enclosingElement as? TypeElement)
                ?.qualifiedName
                ?.toString()
                .orEmpty()
            if (isLitePalSupportBoundary(ownerName)) {
                return@forEach
            }
            val columnConfig = readColumnConfig(field)
            if (columnConfig.ignore) {
                return@forEach
            }
            val normalizedTypeName = normalizeTypeName(field.asType().toString())
            val rawTypeName = normalizeRawType(normalizedTypeName)
            val collectionType = resolveCollectionType(normalizedTypeName)
            val collectionElementTypeName = normalizeCollectionElementType(normalizedTypeName)
            if (hasPublicGetter(typeElement, fieldName, normalizedTypeName)) {
                declaredProperties.add(
                    PropertyModel(
                        propertyName = fieldName,
                        sourceTypeName = field.asType().toString(),
                        writable = hasPublicSetter(typeElement, fieldName, normalizedTypeName),
                        normalizedTypeName = rawTypeName,
                        collectionType = collectionType,
                        collectionElementTypeName = collectionElementTypeName,
                        encryptAlgorithm = readEncryptAlgorithm(field)
                    )
                )
            }
            if (isSupportedFieldType(rawTypeName)) {
                if (!hasPublicAccessors(typeElement, field, normalizedTypeName)) {
                    return@forEach
                }
                supportedFields.add(fieldName)
                persistedFields.add(
                    PersistentFieldModel(
                        propertyName = fieldName,
                        columnName = resolveColumnName(fieldName),
                        typeName = rawTypeName,
                        columnType = mapToColumnType(rawTypeName),
                        nullable = columnConfig.nullable,
                        unique = columnConfig.unique,
                        hasIndex = columnConfig.indexed,
                        defaultValue = columnConfig.defaultValue,
                        encryptAlgorithm = readEncryptAlgorithm(field)
                    )
                )
            }
        }

        return EntityModel(
            className = className,
            tableName = tableName,
            supportedFields = supportedFields.toList().sorted(),
            supportedGenericFields = emptyList(),
            declaredProperties = declaredProperties.sortedBy { it.propertyName },
            hasNoArgsConstructor = hasNoArgsConstructor,
            persistedFields = persistedFields.sortedBy { it.propertyName }
        )
    }

    private fun isLitePalSupportBoundary(className: String): Boolean {
        return className == LITEPAL_SUPPORT_FQN ||
            className == "kotlin.Any" ||
            className == "java.lang.Object"
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

    private fun hasPublicAccessors(
        typeElement: TypeElement,
        field: VariableElement,
        normalizedTypeName: String
    ): Boolean {
        val fieldName = field.simpleName.toString()
        if (!hasPublicGetter(typeElement, fieldName, normalizedTypeName)) {
            processingEnvRef.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Persistent field '$fieldName' must expose a public getter. " +
                    "Please provide public getter/setter accessors.",
                field
            )
            hasCompilationError = true
            return false
        }

        if (!hasPublicSetter(typeElement, fieldName, normalizedTypeName)) {
            processingEnvRef.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Persistent field '$fieldName' must expose a public setter. " +
                    "Please declare it mutable and provide public getter/setter.",
                field
            )
            hasCompilationError = true
            return false
        }
        return true
    }

    private fun hasPublicGetter(typeElement: TypeElement, fieldName: String, normalizedTypeName: String): Boolean {
        val methods = processingEnvRef.elementUtils
            .getAllMembers(typeElement)
            .filterIsInstance<ExecutableElement>()
        val getterNames = getterCandidates(fieldName, normalizedTypeName)
        return methods.any { method ->
            method.modifiers.contains(Modifier.PUBLIC) &&
                method.parameters.isEmpty() &&
                getterNames.contains(method.simpleName.toString())
        }
    }

    private fun hasPublicSetter(typeElement: TypeElement, fieldName: String, normalizedTypeName: String): Boolean {
        val methods = processingEnvRef.elementUtils
            .getAllMembers(typeElement)
            .filterIsInstance<ExecutableElement>()
        val setterName = setterCandidate(fieldName, normalizedTypeName)
        return methods.any { method ->
            method.modifiers.contains(Modifier.PUBLIC) &&
                method.parameters.size == 1 &&
                method.simpleName.toString() == setterName
        }
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
            "boolean" -> "java.lang.Boolean"
            "float" -> "java.lang.Float"
            "double" -> "java.lang.Double"
            "int" -> "java.lang.Integer"
            "long" -> "java.lang.Long"
            "short" -> "java.lang.Short"
            "char" -> "java.lang.Character"
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

    private fun normalizeRawType(typeName: String): String {
        return if (typeName.contains("<")) {
            typeName.substringBefore("<").trim()
        } else {
            typeName.trim()
        }
    }

    private fun normalizeCollectionElementType(typeName: String): String? {
        if (!typeName.contains("<") || !typeName.contains(">")) {
            return null
        }
        val raw = typeName.substringAfter("<").substringBeforeLast(">").trim()
        if (raw.isBlank()) {
            return null
        }
        return normalizeTypeName(raw)
    }

    private fun resolveCollectionType(typeName: String): String? {
        return when {
            typeName.startsWith("java.util.List<") ||
                typeName.startsWith("kotlin.collections.List<") ||
                typeName.startsWith("kotlin.collections.MutableList<") -> "LIST"

            typeName.startsWith("java.util.Set<") ||
                typeName.startsWith("kotlin.collections.Set<") ||
                typeName.startsWith("kotlin.collections.MutableSet<") -> "SET"

            else -> null
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
        val kaptKotlinGeneratedDir = processingEnvRef.options["kapt.kotlin.generated"]
        if (!kaptKotlinGeneratedDir.isNullOrBlank()) {
            val outputFile = File(
                kaptKotlinGeneratedDir,
                "org/litepal/generated/LitePalGeneratedRegistryImpl.kt"
            )
            outputFile.parentFile.mkdirs()
            outputFile.writeText(source)
        } else {
            processingEnvRef.messager.printMessage(
                Diagnostic.Kind.WARNING,
                "kapt.kotlin.generated is not configured; fallback to Java source output for LitePalGeneratedRegistryImpl."
            )
            val sourceFile = filer.createSourceFile("org.litepal.generated.LitePalGeneratedRegistryImpl")
            sourceFile.openWriter().use { it.write(source) }
        }

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
            typeName.startsWith("kotlin.collections.Set<") ||
            typeName.startsWith("kotlin.collections.MutableList<") ||
            typeName.startsWith("kotlin.collections.MutableSet<")
    }

    private fun getterCandidates(fieldName: String, normalizedTypeName: String): Set<String> {
        val candidates = LinkedHashSet<String>()
        if (isBooleanType(normalizedTypeName)) {
            if (fieldName.startsWith("is") && fieldName.length > 2 && fieldName[2].isUpperCase()) {
                val suffix = fieldName.substring(2)
                candidates.add(fieldName)
                candidates.add("get$suffix")
            } else {
                val cap = capitalize(fieldName)
                candidates.add("is$cap")
                candidates.add("get$cap")
            }
        } else {
            candidates.add("get${capitalize(fieldName)}")
        }
        return candidates
    }

    private fun setterCandidate(fieldName: String, normalizedTypeName: String): String {
        if (isBooleanType(normalizedTypeName) &&
            fieldName.startsWith("is") &&
            fieldName.length > 2 &&
            fieldName[2].isUpperCase()
        ) {
            return "set${fieldName.substring(2)}"
        }
        return "set${capitalize(fieldName)}"
    }

    private fun isBooleanType(typeName: String): Boolean {
        return typeName == "boolean" || typeName == "java.lang.Boolean" || typeName == "kotlin.Boolean"
    }

    private fun capitalize(value: String): String {
        if (value.isBlank()) {
            return value
        }
        return value.substring(0, 1).uppercase() + value.substring(1)
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
        private const val LITEPAL_SUPPORT_FQN = "org.litepal.crud.LitePalSupport"
        private const val COLUMN_FQN = "org.litepal.annotation.Column"
        private const val ENCRYPT_FQN = "org.litepal.annotation.Encrypt"
    }
}
