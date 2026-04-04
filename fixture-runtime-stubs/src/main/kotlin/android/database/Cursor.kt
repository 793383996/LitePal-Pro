package android.database

interface Cursor {
    fun getColumnIndex(columnName: String?): Int
    fun isNull(columnIndex: Int): Boolean
    fun getInt(columnIndex: Int): Int
    fun getFloat(columnIndex: Int): Float
    fun getDouble(columnIndex: Int): Double
    fun getLong(columnIndex: Int): Long
    fun getShort(columnIndex: Int): Short
    fun getString(columnIndex: Int): String?
}
