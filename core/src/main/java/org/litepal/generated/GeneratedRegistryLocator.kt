package org.litepal.generated

import org.litepal.LitePalRuntime
import org.litepal.crud.LitePalSupport
import org.litepal.util.LitePalLog
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean

object GeneratedRegistryLocator {

    private const val TAG = "GeneratedRegistryLocator"

    @Volatile
    private var loadedRegistry: LitePalGeneratedRegistry? = null
    @Volatile
    private var testingRegistry: LitePalGeneratedRegistry? = null
    private val attempted = AtomicBoolean(false)

    @JvmStatic
    fun registry(): LitePalGeneratedRegistry? {
        testingRegistry?.let { return it }
        if (attempted.get()) {
            return loadedRegistry
        }
        synchronized(this) {
            testingRegistry?.let { return it }
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
        installRegistryForTesting(null)
    }

    @JvmStatic
    internal fun installRegistryForTesting(registry: LitePalGeneratedRegistry?) {
        synchronized(this) {
            testingRegistry = registry
            loadedRegistry = null
            attempted.set(false)
        }
    }

    private fun loadRegistry(): LitePalGeneratedRegistry? {
        return try {
            val serviceLoader = ServiceLoader.load(
                LitePalGeneratedRegistry::class.java,
                LitePalGeneratedRegistry::class.java.classLoader
            )
            val iterator = serviceLoader.iterator()
            if (!iterator.hasNext()) {
                LitePalLog.d(TAG, "No generated LitePal registry service found in classpath.")
                LitePalRuntime.recordGeneratedContractViolation("registry.missing")
                return null
            }
            val first = iterator.next()
            if (iterator.hasNext()) {
                LitePalRuntime.recordGeneratedContractViolation("registry.multiple")
                throw IllegalStateException(
                    "Multiple LitePal generated registries were discovered via ServiceLoader. " +
                        "Please keep exactly one @LitePalSchemaAnchor in the app classpath."
                )
            }
            first
        } catch (e: IllegalStateException) {
            // Keep explicit cardinality/configuration diagnostics for callers.
            throw e
        } catch (t: Throwable) {
            LitePalLog.e(TAG, "Failed to load generated LitePal registry.", t)
            LitePalRuntime.recordGeneratedContractViolation("registry.load.failed")
            null
        }
    }
}
