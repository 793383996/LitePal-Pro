package org.litepal.sampletest.model

import org.litepal.crud.LitePalSupport
import kotlin.jvm.JvmName

class Teacher : LitePalSupport() {
    var id: Int = 0
    var teacherName: String = ""
    var sex: Boolean = true
    var age: Int = 22
    var teachYears: Int = 0
    var idCard: IdCard? = null
    var students: MutableList<Student> = mutableListOf()

    @get:JvmName("isSexCompat")
    @set:JvmName("setIsSexCompat")
    var isSex: Boolean
        get() = sex
        set(value) {
            sex = value
        }
}




