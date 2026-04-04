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

import android.text.TextUtils
import org.litepal.exceptions.InvalidAttributesException
import org.litepal.generated.GeneratedRegistryLocator
import org.litepal.util.BaseUtility
import org.litepal.util.Const
import org.litepal.util.LitePalLog
import org.litepal.util.SharedUtil

class LitePalAttr private constructor() {

    var version: Int = 0
    var dbName: String? = null
    var cases: String? = null
    var storage: String? = null
    private var classNames: MutableList<String>? = null
    var extraKeyName: String? = null

    fun getClassNames(): MutableList<String> {
        val list = classNames ?: mutableListOf<String>().also { classNames = it }
        if (list.isEmpty()) {
            list.addAll(loadAnchorEntitiesOrThrow())
        }
        if (!list.contains("org.litepal.model.Table_Schema")) {
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

    fun checkSelfValid() {
        if (TextUtils.isEmpty(dbName)) {
            loadLitePalXMLConfiguration()
            if (TextUtils.isEmpty(dbName)) {
                throw InvalidAttributesException(InvalidAttributesException.DBNAME_IS_EMPTY_OR_NOT_DEFINED)
            }
        }
        if (!dbName!!.endsWith(Const.Config.DB_NAME_SUFFIX)) {
            dbName += Const.Config.DB_NAME_SUFFIX
        }
        if (version < 1) {
            throw InvalidAttributesException(InvalidAttributesException.VERSION_OF_DATABASE_LESS_THAN_ONE)
        }
        if (version < SharedUtil.getLastVersion(extraKeyName)) {
            throw InvalidAttributesException(InvalidAttributesException.VERSION_IS_EARLIER_THAN_CURRENT)
        }
        if (TextUtils.isEmpty(cases)) {
            cases = Const.Config.CASES_LOWER
        } else if (cases != Const.Config.CASES_UPPER && cases != Const.Config.CASES_LOWER && cases != Const.Config.CASES_KEEP) {
            throw InvalidAttributesException("$cases${InvalidAttributesException.CASES_VALUE_IS_INVALID}")
        }
    }

    companion object {
        @Volatile
        private var litePalAttr: LitePalAttr? = null

        @JvmStatic
        fun getInstance(): LitePalAttr {
            val existing = litePalAttr
            if (existing != null) {
                return existing
            }
            return synchronized(LitePalAttr::class.java) {
                val existingAfterLock = litePalAttr
                if (existingAfterLock != null) {
                    existingAfterLock
                } else {
                    LitePalAttr().also {
                        litePalAttr = it
                        loadLitePalXMLConfiguration()
                    }
                }
            }
        }

        private fun loadLitePalXMLConfiguration() {
            val target = litePalAttr ?: return
            target.setClassNames(loadAnchorEntitiesOrThrow())
            if (BaseUtility.isLitePalXMLExists()) {
                val config = LitePalParser.parseLitePalConfiguration()
                target.dbName = config.dbName
                target.version = config.version
                target.cases = config.cases
                target.storage = config.storage
            }
        }

        private fun loadAnchorEntitiesOrThrow(): List<String> {
            val anchorEntities = GeneratedRegistryLocator.anchorEntities()
            if (anchorEntities.isNotEmpty()) {
                return anchorEntities
            }
            LitePalLog.e("LitePalAttr", "Missing generated anchor entities.")
            throw IllegalStateException(
                "LitePal requires generated metadata from @LitePalSchemaAnchor. " +
                    "Please configure KSP/KAPT and declare exactly one anchor."
            )
        }

        @JvmStatic
        fun clearInstance() {
            litePalAttr = null
        }
    }
}
