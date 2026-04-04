package org.litepal.util

object BaseUtility {
    @JvmStatic
    fun changeCase(value: String?): String? = value
}

object DBUtility {
    @JvmStatic
    fun convertToValidColumnName(column: String?): String {
        return column.orEmpty()
    }
}
