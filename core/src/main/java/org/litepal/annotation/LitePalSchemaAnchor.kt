package org.litepal.annotation

import org.litepal.crud.LitePalSupport
import kotlin.reflect.KClass

/**
 * Compile-time schema anchor for LitePal generated metadata.
 *
 * Exactly one anchor should be declared in an application/module that owns LitePal entities.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class LitePalSchemaAnchor(
    val version: Int,
    val entities: Array<KClass<out LitePalSupport>>
)
