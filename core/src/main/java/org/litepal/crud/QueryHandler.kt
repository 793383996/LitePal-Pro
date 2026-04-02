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

package org.litepal.crud

import android.database.sqlite.SQLiteDatabase
import org.litepal.util.BaseUtility
import org.litepal.util.DBUtility

class QueryHandler(db: SQLiteDatabase) : DataHandler() {

    init {
        mDatabase = db
    }

    fun <T> onFind(modelClass: Class<T>, id: Long, isEager: Boolean): T? {
        val dataList = query(
            modelClass,
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            null,
            getForeignKeyAssociations(modelClass.name, isEager)
        )
        return if (dataList.size > 0) dataList[0] else null
    }

    fun <T> onFindFirst(modelClass: Class<T>, isEager: Boolean): T? {
        val dataList = query(
            modelClass,
            null,
            null,
            null,
            null,
            null,
            "id",
            "1",
            getForeignKeyAssociations(modelClass.name, isEager)
        )
        return if (dataList.size > 0) dataList[0] else null
    }

    fun <T> onFindLast(modelClass: Class<T>, isEager: Boolean): T? {
        val dataList = query(
            modelClass,
            null,
            null,
            null,
            null,
            null,
            "id desc",
            "1",
            getForeignKeyAssociations(modelClass.name, isEager)
        )
        return if (dataList.size > 0) dataList[0] else null
    }

    fun <T> onFindAll(modelClass: Class<T>, isEager: Boolean, vararg ids: Long): List<T> {
        return if (isAffectAllLines(ids)) {
            query(
                modelClass,
                null,
                null,
                null,
                null,
                null,
                "id",
                null,
                getForeignKeyAssociations(modelClass.name, isEager)
            )
        } else {
            val result = ArrayList<T>()
            val chunkSize = 500
            val associations = getForeignKeyAssociations(modelClass.name, isEager)
            val loopCount = (ids.size - 1) / chunkSize
            for (i in 0..loopCount) {
                val begin = chunkSize * i
                val end = minOf(chunkSize * (i + 1), ids.size)
                if (begin >= end) {
                    continue
                }
                val placeholders = Array(end - begin) { "?" }.joinToString(",")
                val whereClause = "id IN ($placeholders)"
                val whereArgs = Array(end - begin) { index -> ids[begin + index].toString() }
                result.addAll(
                    query(
                        modelClass,
                        null,
                        whereClause,
                        whereArgs,
                        null,
                        null,
                        "id",
                        null,
                        associations
                    )
                )
            }
            result
        }
    }

    fun <T> onFind(
        modelClass: Class<T>,
        columns: Array<String?>?,
        conditions: Array<String?>?,
        orderBy: String?,
        limit: String?,
        isEager: Boolean
    ): List<T> {
        BaseUtility.checkConditionsCorrect(*conditions.orEmpty())
        var mutableOrderBy = orderBy
        if (!conditions.isNullOrEmpty()) {
            conditions[0] = DBUtility.convertWhereClauseToColumnName(conditions[0])
        }
        mutableOrderBy = DBUtility.convertOrderByClauseToValidName(mutableOrderBy)
        return query(
            modelClass,
            columns,
            getWhereClause(*conditions.orEmpty()),
            getWhereArgs(*conditions.orEmpty()),
            null,
            null,
            mutableOrderBy,
            limit,
            getForeignKeyAssociations(modelClass.name, isEager)
        )
    }

    fun onCount(tableName: String?, conditions: Array<String?>?): Int {
        BaseUtility.checkConditionsCorrect(*conditions.orEmpty())
        if (!conditions.isNullOrEmpty()) {
            conditions[0] = DBUtility.convertWhereClauseToColumnName(conditions[0])
        }
        return mathQuery(tableName, arrayOf("count(1)"), conditions, Int::class.java)
    }

    fun onAverage(tableName: String?, column: String, conditions: Array<String?>?): Double {
        BaseUtility.checkConditionsCorrect(*conditions.orEmpty())
        if (!conditions.isNullOrEmpty()) {
            conditions[0] = DBUtility.convertWhereClauseToColumnName(conditions[0])
        }
        return mathQuery(tableName, arrayOf("avg($column)"), conditions, Double::class.java)
    }

    fun <T> onMax(tableName: String?, column: String, conditions: Array<String?>?, type: Class<T>): T {
        BaseUtility.checkConditionsCorrect(*conditions.orEmpty())
        if (!conditions.isNullOrEmpty()) {
            conditions[0] = DBUtility.convertWhereClauseToColumnName(conditions[0])
        }
        return mathQuery(tableName, arrayOf("max($column)"), conditions, type)
    }

    fun <T> onMin(tableName: String?, column: String, conditions: Array<String?>?, type: Class<T>): T {
        BaseUtility.checkConditionsCorrect(*conditions.orEmpty())
        if (!conditions.isNullOrEmpty()) {
            conditions[0] = DBUtility.convertWhereClauseToColumnName(conditions[0])
        }
        return mathQuery(tableName, arrayOf("min($column)"), conditions, type)
    }

    fun <T> onSum(tableName: String?, column: String, conditions: Array<String?>?, type: Class<T>): T {
        BaseUtility.checkConditionsCorrect(*conditions.orEmpty())
        if (!conditions.isNullOrEmpty()) {
            conditions[0] = DBUtility.convertWhereClauseToColumnName(conditions[0])
        }
        return mathQuery(tableName, arrayOf("sum($column)"), conditions, type)
    }
}
