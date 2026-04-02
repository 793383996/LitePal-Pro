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

import org.litepal.util.BaseUtility

class TableModel {
    private var tableName: String? = null
    private val columnModelMap: MutableMap<String, ColumnModel> = HashMap()
    private var className: String? = null

    fun getTableName(): String? = tableName

    fun setTableName(tableName: String?) {
        this.tableName = tableName
    }

    fun getClassName(): String? = className

    fun setClassName(className: String?) {
        this.className = className
    }

    fun addColumnModel(columnModel: ColumnModel) {
        columnModelMap[BaseUtility.changeCase(columnModel.getColumnName()).orEmpty()] = columnModel
    }

    fun getColumnModels(): Collection<ColumnModel> = columnModelMap.values

    fun getColumnModelByName(columnName: String?): ColumnModel? {
        return columnModelMap[BaseUtility.changeCase(columnName).orEmpty()]
    }

    fun removeColumnModelByName(columnName: String?) {
        columnModelMap.remove(BaseUtility.changeCase(columnName).orEmpty())
    }

    fun containsColumn(columnName: String?): Boolean {
        return columnModelMap.containsKey(BaseUtility.changeCase(columnName).orEmpty())
    }
}
