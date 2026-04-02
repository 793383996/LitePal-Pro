package com.litepaltest.model

import org.litepal.crud.LitePalSupport

class Product() : LitePalSupport() {
    var id: Int = 0
    var brand: String? = null
    var price: Double = 0.0
    var pic: ByteArray? = null

    constructor(p: Product?) : this()
}
