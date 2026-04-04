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

package org.litepal.util

import android.content.res.AssetManager
import android.text.TextUtils
import org.litepal.LitePalApplication
import org.litepal.exceptions.LitePalSupportException
import org.litepal.parser.LitePalAttr
import java.io.IOException
import java.util.Locale

object BaseUtility {

    @JvmStatic
    fun changeCase(string: String?): String? {
        if (string != null) {
            val litePalAttr = LitePalAttr.getInstance()
            val cases = litePalAttr.cases
            if (Const.Config.CASES_KEEP == cases) {
                return string
            } else if (Const.Config.CASES_UPPER == cases) {
                return string.uppercase(Locale.US)
            }
            return string.lowercase(Locale.US)
        }
        return null
    }

    @JvmStatic
    fun containsIgnoreCases(collection: Collection<String>?, string: String?): Boolean {
        if (collection == null) {
            return false
        }
        if (string == null) {
            return false
        }
        var contains = false
        for (element in collection) {
            if (string.equals(element, ignoreCase = true)) {
                contains = true
                break
            }
        }
        return contains
    }

    @JvmStatic
    fun capitalize(string: String?): String? {
        if (!TextUtils.isEmpty(string)) {
            val nonNull = string!!
            return nonNull.substring(0, 1).uppercase(Locale.US) + nonNull.substring(1)
        }
        return if (string == null) null else ""
    }

    @JvmStatic
    fun count(string: String?, mark: String?): Int {
        if (!TextUtils.isEmpty(string) && !TextUtils.isEmpty(mark)) {
            var source = string!!
            var count = 0
            val target = mark!!
            var index = source.indexOf(target)
            while (index != -1) {
                count++
                source = source.substring(index + target.length)
                index = source.indexOf(target)
            }
            return count
        }
        return 0
    }

    @JvmStatic
    fun checkConditionsCorrect(vararg conditions: String?) {
        if (conditions.isNotEmpty()) {
            val whereClause = conditions[0]
            val placeHolderSize = count(whereClause, "?")
            if (conditions.size != placeHolderSize + 1) {
                throw LitePalSupportException(LitePalSupportException.UPDATE_CONDITIONS_EXCEPTION)
            }
        }
    }

    @JvmStatic
    fun isFieldTypeSupported(fieldType: String?): Boolean {
        return "boolean" == fieldType || "java.lang.Boolean" == fieldType ||
            "float" == fieldType || "java.lang.Float" == fieldType ||
            "double" == fieldType || "java.lang.Double" == fieldType ||
            "int" == fieldType || "java.lang.Integer" == fieldType ||
            "long" == fieldType || "java.lang.Long" == fieldType ||
            "short" == fieldType || "java.lang.Short" == fieldType ||
            "char" == fieldType || "java.lang.Character" == fieldType ||
            "java.lang.String" == fieldType || "java.util.Date" == fieldType
    }

    @JvmStatic
    fun isGenericTypeSupported(genericType: String?): Boolean {
        return "java.lang.String" == genericType ||
            "java.lang.Integer" == genericType ||
            "java.lang.Float" == genericType ||
            "java.lang.Double" == genericType ||
            "java.lang.Long" == genericType ||
            "java.lang.Short" == genericType ||
            "java.lang.Boolean" == genericType ||
            "java.lang.Character" == genericType
    }

    @JvmStatic
    fun isLitePalXMLExists(): Boolean {
        try {
            val assetManager: AssetManager = LitePalApplication.getContext().assets
            val fileNames = assetManager.list("")
            if (!fileNames.isNullOrEmpty()) {
                for (fileName in fileNames) {
                    if (Const.Config.CONFIGURATION_FILE_NAME.equals(fileName, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (_: IOException) {
        }
        return false
    }

}
