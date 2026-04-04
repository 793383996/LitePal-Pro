package org.litepal.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import org.litepal.compiler.common.AnchorModel
import org.litepal.compiler.common.EntityModel
import org.litepal.compiler.common.PersistentFieldModel
import org.litepal.compiler.common.PropertyModel
import org.litepal.compiler.common.RelationshipModeling
import org.litepal.compiler.common.RegistryRendering
import java.util.Locale

class LitePalSchemaKspProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return LitePalSchemaKspProcessor(environment.codeGenerator, environment.logger)
    }
}

private class LitePalSchemaKspProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private var processed = false
    private var hasCompilationError = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) {
            return emptyList()
        }
        processed = true

        val anchors = resolver
            .getSymbolsWithAnnotation(SCHEMA_ANCHOR_FQN)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        if (anchors.isEmpty()) {
            logger.error("LitePal requires exactly one @LitePalSchemaAnchor. None found.")
            return emptyList()
        }
        if (anchors.size > 1) {
            logger.error("LitePal requires exactly one @LitePalSchemaAnchor. Found ${anchors.size}.")
            return emptyList()
        }

        val anchor = anchors.first()
        val anchorAnnotation = anchor.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SCHEMA_ANCHOR_FQN
        }
        if (anchorAnnotation == null) {
            logger.error("Unable to resolve @LitePalSchemaAnchor on ${anchor.simpleName.asString()}.")
            return emptyList()
        }

        val version = (anchorAnnotation.arguments.firstOrNull { it.name?.asString() == "version" }?.value as? Int) ?: 1
        val entityTypes = (anchorAnnotation.arguments.firstOrNull { it.name?.asString() == "entities" }?.value as? List<*>)
            .orEmpty()
            .mapNotNull { (it as? KSType)?.declaration as? KSClassDeclaration }

        if (entityTypes.isEmpty()) {
            logger.error("@LitePalSchemaAnchor must declare non-empty entities.", anchor)
            return emptyList()
        }

        val rawEntities = entityTypes.map { entityDecl ->
            toEntityModel(entityDecl)
        }
        val entities = RelationshipModeling.enrichEntities(rawEntities)
        if (hasCompilationError) {
            return emptyList()
        }

        val model = AnchorModel(
            version = version,
            anchorClassName = anchor.qualifiedName?.asString().orEmpty(),
            entities = entities
        )

        val dependencyFiles = buildList {
            anchor.containingFile?.let(::add)
            entityTypes.forEach { it.containingFile?.let(::add) }
        }.toTypedArray()
        val dependencies = Dependencies(aggregating = true, *dependencyFiles)

        val registrySource = RegistryRendering.generatedRegistrySource(model)
        codeGenerator.createNewFile(
            dependencies,
            "org.litepal.generated",
            "LitePalGeneratedRegistryImpl",
            "kt"
        ).bufferedWriter().use { it.write(registrySource) }

        val schemaJson = RegistryRendering.schemaJson(model)
        val schemaHash = RegistryRendering.schemaHash(schemaJson)
        val migrationReport = RegistryRendering.migrationReport(model)

        codeGenerator.createNewFile(
            dependencies,
            "org.litepal.generated",
            "schema-v${model.version}",
            "json"
        ).bufferedWriter().use { it.write(schemaJson) }

        codeGenerator.createNewFile(
            dependencies,
            "org.litepal.generated",
            "schema-hash",
            "txt"
        ).bufferedWriter().use { it.write(schemaHash) }

        codeGenerator.createNewFile(
            dependencies,
            "org.litepal.generated",
            "migration-diff-report",
            "txt"
        ).bufferedWriter().use { it.write(migrationReport) }

        return emptyList()
    }

    private fun toEntityModel(entityDecl: KSClassDeclaration): EntityModel {
        val className = entityDecl.qualifiedName?.asString().orEmpty()
        val tableName = entityDecl.simpleName.asString()
        val hasNoArgsConstructor = hasNoArgsConstructor(entityDecl)

        val supportedFields = linkedSetOf<String>()
        val declaredProperties = ArrayList<PropertyModel>()
        val persistedFields = ArrayList<PersistentFieldModel>()

        collectPersistedProperties(entityDecl).forEach { property ->
            val columnConfig = readColumnConfig(property)
            if (columnConfig.ignore) {
                return@forEach
            }
            val propertyName = property.simpleName.asString()
            val resolvedType = property.type.resolve()
            val rawTypeName = resolvedType.declaration.qualifiedName?.asString().orEmpty()
            val normalizedTypeName = normalizeTypeName(rawTypeName)
            val collectionType = resolveCollectionType(normalizedTypeName)
            val genericTypeName = resolvedType.arguments.firstOrNull()?.type?.resolve()
                ?.declaration?.qualifiedName?.asString()
                .orEmpty()
            val normalizedGenericTypeName = normalizeTypeName(genericTypeName)
            if (hasPublicGetter(property)) {
                declaredProperties.add(
                    PropertyModel(
                        propertyName = propertyName,
                        sourceTypeName = resolvedType.toString(),
                        writable = hasPublicSetter(property),
                        normalizedTypeName = normalizedTypeName,
                        collectionType = collectionType,
                        collectionElementTypeName = normalizedGenericTypeName.takeIf { collectionType != null && it.isNotBlank() },
                        encryptAlgorithm = readEncryptAlgorithm(property)
                    )
                )
            }
            if (isSupportedFieldType(normalizedTypeName)) {
                if (!hasPublicAccessors(property, normalizedTypeName)) {
                    return@forEach
                }
                supportedFields.add(propertyName)
                persistedFields.add(
                    PersistentFieldModel(
                        propertyName = propertyName,
                        columnName = resolveColumnName(propertyName),
                        typeName = normalizedTypeName,
                        columnType = mapToColumnType(normalizedTypeName),
                        nullable = columnConfig.nullable,
                        unique = columnConfig.unique,
                        hasIndex = columnConfig.indexed,
                        defaultValue = columnConfig.defaultValue,
                        encryptAlgorithm = readEncryptAlgorithm(property)
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

    private fun collectPersistedProperties(entityDecl: KSClassDeclaration): List<KSPropertyDeclaration> {
        val allProperties = entityDecl.getAllProperties().toList()
        val propertyNameSet = allProperties
            .map { property -> property.simpleName.asString() }
            .toSet()
        val collected = LinkedHashMap<String, KSPropertyDeclaration>()
        for (property in allProperties) {
            val propertyName = property.simpleName.asString()
            if (propertyName == "Companion" || propertyName.startsWith("$")) {
                continue
            }
            if (property.isStatic()) {
                continue
            }
            val ownerName = (property.parentDeclaration as? KSClassDeclaration)
                ?.qualifiedName
                ?.asString()
                .orEmpty()
            if (isLitePalSupportBoundary(ownerName)) {
                continue
            }
            val resolvedType = normalizeTypeName(
                property.type.resolve().declaration.qualifiedName?.asString().orEmpty()
            )
            if (isBooleanAliasProperty(propertyName, resolvedType, propertyNameSet)) {
                continue
            }
            if (!collected.containsKey(propertyName)) {
                collected[propertyName] = property
            }
        }
        return collected.values.toList()
    }

    private fun isLitePalSupportBoundary(className: String): Boolean {
        return className == LITEPAL_SUPPORT_FQN ||
            className == "kotlin.Any" ||
            className == "java.lang.Object"
    }

    private fun isBooleanAliasProperty(propertyName: String, normalizedTypeName: String, propertyNameSet: Set<String>): Boolean {
        if (!propertyName.startsWith("is") || propertyName.length <= 2) {
            return false
        }
        if (normalizedTypeName != "boolean" && normalizedTypeName != "java.lang.Boolean") {
            return false
        }
        val aliasName = propertyName.substring(2, 3).lowercase(Locale.US) + propertyName.substring(3)
        return aliasName in propertyNameSet
    }

    private fun resolveColumnName(propertyName: String): String {
        return if ("_id".equals(propertyName, ignoreCase = true) || "id".equals(propertyName, ignoreCase = true)) {
            "id"
        } else {
            propertyName
        }
    }

    private fun hasNoArgsConstructor(entityDecl: KSClassDeclaration): Boolean {
        val constructors = entityDecl.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { declaration -> declaration.simpleName.asString() == "<init>" }
            .toList()
        if (constructors.isEmpty()) {
            return true
        }
        return constructors.any { constructor -> constructor.parameters.isEmpty() }
    }

    private fun hasPublicAccessors(property: KSPropertyDeclaration, normalizedTypeName: String): Boolean {
        if (!hasPublicGetter(property)) {
            logger.error(
                "Persistent property '${property.simpleName.asString()}' must expose a public getter. " +
                    "Please declare it as public and provide getter/setter.",
                property
            )
            hasCompilationError = true
            return false
        }
        if (!hasPublicSetter(property)) {
            logger.error(
                "Persistent property '${property.simpleName.asString()}' must expose a public setter. " +
                    "Please declare it as mutable public var with getter/setter.",
                property
            )
            hasCompilationError = true
            return false
        }
        if (normalizedTypeName.isBlank()) {
            logger.error(
                "Persistent property '${property.simpleName.asString()}' has unsupported type metadata.",
                property
            )
            hasCompilationError = true
            return false
        }
        return true
    }

    private fun hasPublicGetter(property: KSPropertyDeclaration): Boolean {
        val getter = property.getter ?: return false
        return getter.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.PUBLIC)
    }

    private fun hasPublicSetter(property: KSPropertyDeclaration): Boolean {
        val setter = property.setter ?: return false
        return setter.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.PUBLIC)
    }

    private fun readColumnConfig(property: KSPropertyDeclaration): ColumnConfig {
        val annotation = property.findAnnotation(COLUMN_FQN) ?: return ColumnConfig()
        return ColumnConfig(
            nullable = annotation.booleanArg("nullable", true),
            unique = annotation.booleanArg("unique", false),
            defaultValue = annotation.stringArg("defaultValue", ""),
            ignore = annotation.booleanArg("ignore", false),
            indexed = annotation.booleanArg("index", false)
        )
    }

    private fun readEncryptAlgorithm(property: KSPropertyDeclaration): String? {
        val annotation = property.findAnnotation(ENCRYPT_FQN) ?: return null
        val algorithm = annotation.stringArg("algorithm", "")
        return algorithm.takeIf { it.isNotBlank() }
    }

    private fun KSPropertyDeclaration.findAnnotation(annotationFqn: String): KSAnnotation? {
        return annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationFqn
        }
    }

    private fun KSAnnotation.booleanArg(name: String, defaultValue: Boolean): Boolean {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? Boolean ?: defaultValue
    }

    private fun KSAnnotation.stringArg(name: String, defaultValue: String): String {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? String ?: defaultValue
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
        return typeName == "kotlin.collections.List" ||
            typeName == "kotlin.collections.MutableList" ||
            typeName == "kotlin.collections.Set" ||
            typeName == "kotlin.collections.MutableSet" ||
            typeName == "java.util.List" ||
            typeName == "java.util.Set"
    }

    private fun resolveCollectionType(typeName: String): String? {
        return when (typeName) {
            "kotlin.collections.List",
            "kotlin.collections.MutableList",
            "java.util.List" -> "LIST"

            "kotlin.collections.Set",
            "kotlin.collections.MutableSet",
            "java.util.Set" -> "SET"

            else -> null
        }
    }

    private fun KSPropertyDeclaration.isStatic(): Boolean {
        if (this.extensionReceiver != null || this.isDelegated()) {
            return true
        }
        val parentDeclaration = this.parentDeclaration
        if (parentDeclaration is KSClassDeclaration) {
            return parentDeclaration.classKind.name == "OBJECT"
        }
        return false
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
