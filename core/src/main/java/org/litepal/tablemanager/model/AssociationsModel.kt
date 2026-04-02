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

import java.util.Locale

class AssociationsModel {
    private var tableName: String? = null
    private var associatedTableName: String? = null
    private var tableHoldsForeignKey: String? = null
    private var associationType: Int = 0

    fun getTableName(): String? = tableName

    fun setTableName(tableName: String?) {
        this.tableName = tableName
    }

    fun getAssociatedTableName(): String? = associatedTableName

    fun setAssociatedTableName(associatedTableName: String?) {
        this.associatedTableName = associatedTableName
    }

    fun getTableHoldsForeignKey(): String? = tableHoldsForeignKey

    fun setTableHoldsForeignKey(tableHoldsForeignKey: String?) {
        this.tableHoldsForeignKey = tableHoldsForeignKey
    }

    fun getAssociationType(): Int = associationType

    fun setAssociationType(associationType: Int) {
        this.associationType = associationType
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AssociationsModel) return false
        val otherTable = other.getTableName()
        val otherAssociated = other.getAssociatedTableName()
        val otherHoldsFk = other.getTableHoldsForeignKey()
        if (otherTable == null || otherAssociated == null || otherHoldsFk == null) return false
        if (tableName == null || associatedTableName == null || tableHoldsForeignKey == null) return false
        if (other.getAssociationType() != associationType) return false
        if (otherHoldsFk != tableHoldsForeignKey) return false
        if (otherTable == tableName && otherAssociated == associatedTableName && otherHoldsFk == tableHoldsForeignKey) {
            return true
        }
        return otherTable == associatedTableName && otherAssociated == tableName && otherHoldsFk == tableHoldsForeignKey
    }

    override fun hashCode(): Int {
        val localTable = tableName ?: return 0
        val localAssociatedTable = associatedTableName ?: return 0
        val localHoldsFk = tableHoldsForeignKey ?: return 0
        val normalizedTable = localTable.lowercase(Locale.US)
        val normalizedAssociatedTable = localAssociatedTable.lowercase(Locale.US)
        val first = if (normalizedTable <= normalizedAssociatedTable) {
            normalizedTable
        } else {
            normalizedAssociatedTable
        }
        val second = if (normalizedTable <= normalizedAssociatedTable) {
            normalizedAssociatedTable
        } else {
            normalizedTable
        }
        var result = associationType
        result = 31 * result + localHoldsFk.lowercase(Locale.US).hashCode()
        result = 31 * result + first.hashCode()
        result = 31 * result + second.hashCode()
        return result
    }
}
