package com.litepaltest.model

import org.litepal.crud.LitePalSupport
import java.io.Serializable

class Book : LitePalSupport(), Serializable {
    var id: Long = 0
    var bookName: String? = null
    var pages: Int? = null
    var price: Double = 0.0
    var level: Char = '\u0000'
    var isbn: Short = 0
    var isPublished: Boolean = false
    var area: Float = 0f

    companion object {
        private const val serialVersionUID: Long = 9040804172147110007L
    }
}
