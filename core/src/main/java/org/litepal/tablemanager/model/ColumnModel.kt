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

package org.litepal.tablemanager.model

import android.text.TextUtils

class ColumnModel {
    private var columnName: String? = null
    private var columnType: String? = null
    private var nullable: Boolean = true
    private var unique: Boolean = false
    private var defaultValue: String = ""
    private var indexed: Boolean = false

    fun getColumnName(): String? = columnName

    fun setColumnName(columnName: String?) {
        this.columnName = columnName
    }

    fun getColumnType(): String? = columnType

    fun setColumnType(columnType: String?) {
        this.columnType = columnType
    }

    fun isNullable(): Boolean = nullable

    fun setNullable(nullable: Boolean) {
        this.nullable = nullable
    }

    fun isUnique(): Boolean = unique

    fun setUnique(unique: Boolean) {
        this.unique = unique
    }

    fun getDefaultValue(): String = defaultValue

    fun hasIndex(): Boolean = indexed

    fun setHasIndex(hasIndex: Boolean) {
        indexed = hasIndex
    }

    fun setDefaultValue(defaultValue: String?) {
        if ("text".equals(columnType, ignoreCase = true)) {
            if (!TextUtils.isEmpty(defaultValue)) {
                this.defaultValue = "'$defaultValue'"
            }
        } else {
            this.defaultValue = defaultValue ?: ""
        }
    }

    fun isIdColumn(): Boolean {
        return "_id".equals(columnName, ignoreCase = true) || "id".equals(columnName, ignoreCase = true)
    }
}
