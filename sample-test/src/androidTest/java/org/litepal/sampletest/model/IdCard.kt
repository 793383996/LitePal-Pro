package org.litepal.sampletest.model

import org.litepal.crud.LitePalSupport

class IdCard : LitePalSupport() {
    var id: Int = 0
    var number: String? = null
    var address: String? = null
    var student: Student? = null
    var serial: Long = 0
}




