package com.litepaltest.model

import org.litepal.crud.LitePalSupport

class Teacher : LitePalSupport() {
    var id: Int = 0
    var teacherName: String = ""
    var sex: Boolean = true
    var age: Int = 22
    var teachYears: Int = 0
    var idCard: IdCard? = null
    var students: MutableList<Student> = mutableListOf()

    var isSex: Boolean
        get() = sex
        set(value) {
            sex = value
        }
}
