package com.litepaltest.model

import org.litepal.annotation.Column
import org.litepal.crud.LitePalSupport
import java.util.Date

class Student : LitePalSupport() {
    var id: Int = 0
    var name: String? = null
    var age: Int = 0
    var birthday: Date? = null

    @Column(defaultValue = "1589203961859")
    var schoolDate: Date? = null

    var classroom: Classroom? = null
    var idcard: IdCard? = null
    var teachers: MutableList<Teacher> = mutableListOf()
}
