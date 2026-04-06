package org.litepal.sampletest.model

import org.litepal.crud.LitePalSupport

class Classroom : LitePalSupport() {
    var _id: Int = 0
    var name: String? = null
    var news: MutableList<String> = mutableListOf()
    var numbers: MutableList<Int> = mutableListOf()
    var studentCollection: MutableSet<Student> = linkedSetOf()
    var teachers: MutableList<Teacher?> = mutableListOf()
}




