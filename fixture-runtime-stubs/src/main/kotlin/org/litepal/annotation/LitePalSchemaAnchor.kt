package org.litepal.annotation

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LitePalSchemaAnchor(
    val version: Int = 1,
    val entities: Array<KClass<*>>
)
