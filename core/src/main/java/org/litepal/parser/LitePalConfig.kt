/*
 * Copyright (C)  Tony Green, LitePal Framework Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.litepal.parser

class LitePalConfig {
    var version: Int = 0
    var dbName: String? = null
    var cases: String? = null
    var storage: String? = null
    private var classNames: MutableList<String>? = null

    fun getClassNames(): MutableList<String> {
        val list = classNames ?: mutableListOf<String>().also { classNames = it }
        if (list.isEmpty()) {
            list.add("org.litepal.model.Table_Schema")
        }
        return list
    }

    fun addClassName(className: String) {
        getClassNames().add(className)
    }

    fun setClassNames(classNames: List<String>) {
        this.classNames = classNames.toMutableList()
    }
}
