package org.litepal.generated

import org.litepal.crud.LitePalSupport
import org.litepal.util.LitePalLog
import java.util.concurrent.atomic.AtomicBoolean

object GeneratedRegistryLocator {

    private const val TAG = "GeneratedRegistryLocator"
    private const val DEFAULT_REGISTRY_CLASS = "org.litepal.generated.LitePalGeneratedRegistryImpl"

    @Volatile
    private var loadedRegistry: LitePalGeneratedRegistry? = null
    private val attempted = AtomicBoolean(false)

    @JvmStatic
    fun registry(): LitePalGeneratedRegistry? {
        if (attempted.get()) {
            return loadedRegistry
        }
        synchronized(this) {
            if (attempted.get()) {
                return loadedRegistry
            }
            loadedRegistry = loadRegistry()
            attempted.set(true)
            if (loadedRegistry == null) {
                throw IllegalStateException(
                    "Generated metadata is REQUIRED but no LitePal generated registry was found. " +
                        "Please configure KSP/KAPT and declare exactly one @LitePalSchemaAnchor."
                )
            }
            return loadedRegistry
        }
    }

    @JvmStatic
    fun hasRegistry(): Boolean {
        return try {
            registry() != null
        } catch (_: IllegalStateException) {
            false
        }
    }

    @JvmStatic
    fun anchorEntities(): List<String> = registry()?.anchorEntities.orEmpty()

    @JvmStatic
    fun findEntityMeta(className: String): EntityMeta<out LitePalSupport>? {
        return registry()?.entityMetasByClassName()?.get(className)
    }

    @JvmStatic
    internal fun resetForTesting() {
        synchronized(this) {
            loadedRegistry = null
            attempted.set(false)
        }
    }

    private fun loadRegistry(): LitePalGeneratedRegistry? {
        val className = System.getProperty("litepal.generated.registry") ?: DEFAULT_REGISTRY_CLASS
        return try {
            val clazz = Class.forName(className)
            val instanceField = clazz.fields.firstOrNull { it.name == "INSTANCE" }
            val singletonInstance = instanceField?.get(null) as? LitePalGeneratedRegistry
            if (singletonInstance != null) {
                return singletonInstance
            }
            if (LitePalGeneratedRegistry::class.java.isAssignableFrom(clazz)) {
                val constructor = clazz.getDeclaredConstructor()
                constructor.isAccessible = true
                return constructor.newInstance() as LitePalGeneratedRegistry
            }
            null
        } catch (_: ClassNotFoundException) {
            LitePalLog.d(TAG, "No generated LitePal registry found in classpath.")
            null
        } catch (t: Throwable) {
            LitePalLog.e(TAG, "Failed to load generated LitePal registry.", t)
            null
        }
    }
}
