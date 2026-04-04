package org.litepal.annotation

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Column(
    val nullable: Boolean = true,
    val unique: Boolean = false,
    val defaultValue: String = "",
    val ignore: Boolean = false,
    val index: Boolean = false
)
