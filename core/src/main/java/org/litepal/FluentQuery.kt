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

package org.litepal

import android.text.TextUtils
import org.litepal.crud.QueryHandler
import org.litepal.exceptions.LitePalSupportException
import org.litepal.tablemanager.Connector
import org.litepal.tablemanager.DatabaseRuntimeLock
import org.litepal.util.BaseUtility
import org.litepal.util.DBUtility

class FluentQuery internal constructor() {

    var mColumns: Array<String?>? = null
    var mConditions: Array<String?>? = null
    var mOrderBy: String? = null
    var mLimit: String? = null
    var mOffset: String? = null

    fun select(vararg columns: String?): FluentQuery {
        mColumns = columns.map { it }.toTypedArray()
        return this
    }

    fun where(vararg conditions: String?): FluentQuery {
        mConditions = conditions.map { it }.toTypedArray()
        return this
    }

    fun order(column: String): FluentQuery {
        mOrderBy = column
        return this
    }

    fun limit(value: Int): FluentQuery {
        mLimit = value.toString()
        return this
    }

    fun offset(value: Int): FluentQuery {
        mOffset = value.toString()
        return this
    }

    fun <T> find(modelClass: Class<T>): List<T> {
        return find(modelClass, false)
    }


    fun <T> find(modelClass: Class<T>, isEager: Boolean): List<T> {
        return Operator.runOnQueryExecutor {
            DatabaseRuntimeLock.withReadLock {
            val queryHandler = QueryHandler(Connector.getDatabase())
            val limit = if (mOffset == null) {
                mLimit
            } else {
                if (mLimit == null) {
                    mLimit = "0"
                }
                "${mOffset},${mLimit}"
            }
            queryHandler.onFind(modelClass, mColumns, mConditions, mOrderBy, limit, isEager)
            }
        }
    }


    fun <T> findFirst(modelClass: Class<T>): T? {
        return findFirst(modelClass, false)
    }


    fun <T> findFirst(modelClass: Class<T>, isEager: Boolean): T? {
        return Operator.runOnQueryExecutor {
            DatabaseRuntimeLock.withReadLock {
            val limitTemp = mLimit
            if ("0" != mLimit) {
                mLimit = "1"
            }
            val list = find(modelClass, isEager)
            mLimit = limitTemp
            if (list.isNotEmpty()) {
                if (list.size != 1) {
                    throw LitePalSupportException("Found multiple records while only one record should be found at most.")
                }
                list[0]
            } else {
                null
            }
            }
        }
    }


    fun <T> findLast(modelClass: Class<T>): T? {
        return findLast(modelClass, false)
    }


    fun <T> findLast(modelClass: Class<T>, isEager: Boolean): T? {
        return Operator.runOnQueryExecutor {
            DatabaseRuntimeLock.withReadLock {
            val orderByTemp = mOrderBy
            val limitTemp = mLimit
            if (TextUtils.isEmpty(mOffset) && TextUtils.isEmpty(mLimit)) {
                if (TextUtils.isEmpty(mOrderBy)) {
                    mOrderBy = "id desc"
                } else {
                    mOrderBy = if (mOrderBy!!.endsWith(" desc")) {
                        mOrderBy!!.replace(" desc", "")
                    } else {
                        mOrderBy + " desc"
                    }
                }
                if ("0" != mLimit) {
                    mLimit = "1"
                }
            }
            val list = find(modelClass, isEager)
            mOrderBy = orderByTemp
            mLimit = limitTemp
            val size = list.size
            if (size > 0) {
                list[size - 1]
            } else {
                null
            }
            }
        }
    }


    fun count(modelClass: Class<*>): Int {
        return count(BaseUtility.changeCase(modelClass.simpleName))
    }


    fun count(tableName: String?): Int {
        return Operator.runOnQueryExecutor {
            DatabaseRuntimeLock.withReadLock {
                val queryHandler = QueryHandler(Connector.getDatabase())
                queryHandler.onCount(tableName, mConditions)
            }
        }
    }


    fun average(modelClass: Class<*>, column: String): Double {
        return average(BaseUtility.changeCase(modelClass.simpleName), column)
    }


    fun average(tableName: String?, column: String): Double {
        return Operator.runOnQueryExecutor {
            DatabaseRuntimeLock.withReadLock {
                val queryHandler = QueryHandler(Connector.getDatabase())
                queryHandler.onAverage(tableName, column, mConditions)
            }
        }
    }


    fun <T> max(modelClass: Class<*>, columnName: String, columnType: Class<T>): T {
        return max(BaseUtility.changeCase(modelClass.simpleName), columnName, columnType)
    }


    fun <T> max(tableName: String?, columnName: String, columnType: Class<T>): T {
        return Operator.runOnQueryExecutor {
            DatabaseRuntimeLock.withReadLock {
                val queryHandler = QueryHandler(Connector.getDatabase())
                queryHandler.onMax(tableName, columnName, mConditions, columnType)
            }
        }
    }


    fun <T> min(modelClass: Class<*>, columnName: String, columnType: Class<T>): T {
        return min(BaseUtility.changeCase(modelClass.simpleName), columnName, columnType)
    }


    fun <T> min(tableName: String?, columnName: String, columnType: Class<T>): T {
        return Operator.runOnQueryExecutor {
            DatabaseRuntimeLock.withReadLock {
                val queryHandler = QueryHandler(Connector.getDatabase())
                queryHandler.onMin(tableName, columnName, mConditions, columnType)
            }
        }
    }


    fun <T> sum(modelClass: Class<*>, columnName: String, columnType: Class<T>): T {
        return sum(BaseUtility.changeCase(modelClass.simpleName), columnName, columnType)
    }


    fun <T> sum(tableName: String?, columnName: String, columnType: Class<T>): T {
        return Operator.runOnQueryExecutor {
            DatabaseRuntimeLock.withReadLock {
                val queryHandler = QueryHandler(Connector.getDatabase())
                queryHandler.onSum(tableName, columnName, mConditions, columnType)
            }
        }
    }

}

