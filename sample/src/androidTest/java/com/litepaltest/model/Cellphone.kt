package com.litepaltest.model

import org.litepal.annotation.Column
import org.litepal.crud.LitePalSupport

class Cellphone : LitePalSupport() {
    var id: Long? = null

    @Column(index = true)
    var brand: String? = null

    var inStock: Char? = null
    var price: Double? = null

    @Column(unique = true, nullable = false)
    var serial: String? = null

    @Column(nullable = true, defaultValue = "0.0.0.0")
    var mac: String? = null

    @Column(ignore = true)
    var uuid: String? = null

    var messages: MutableList<WeiboMessage> = mutableListOf()
}
