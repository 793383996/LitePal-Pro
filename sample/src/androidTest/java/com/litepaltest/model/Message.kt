package com.litepaltest.model

import org.litepal.annotation.Column
import org.litepal.crud.LitePalSupport

open class Message : LitePalSupport() {
    var id: Int = 0
    var content: String? = null
    var type: Int = 0

    @Column(ignore = true)
    var title: String? = null
}
