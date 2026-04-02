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

class GenericModel {
    private var tableName: String? = null
    private var valueColumnName: String? = null
    private var valueColumnType: String? = null
    private var valueIdColumnName: String? = null
    private var getMethodName: String? = null

    fun getTableName(): String? = tableName

    fun setTableName(tableName: String?) {
        this.tableName = tableName
    }

    fun getValueColumnName(): String? = valueColumnName

    fun setValueColumnName(valueColumnName: String?) {
        this.valueColumnName = valueColumnName
    }

    fun getValueColumnType(): String? = valueColumnType

    fun setValueColumnType(valueColumnType: String?) {
        this.valueColumnType = valueColumnType
    }

    fun getValueIdColumnName(): String? = valueIdColumnName

    fun setValueIdColumnName(valueIdColumnName: String?) {
        this.valueIdColumnName = valueIdColumnName
    }

    fun getGetMethodName(): String? = getMethodName

    fun setGetMethodName(getMethodName: String?) {
        this.getMethodName = getMethodName
    }
}
