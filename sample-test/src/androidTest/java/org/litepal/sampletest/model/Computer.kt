package org.litepal.sampletest.model

import org.litepal.crud.LitePalSupport

class Computer(var brand: String, var price: Double) : LitePalSupport() {
    var id: Long = 0
}




