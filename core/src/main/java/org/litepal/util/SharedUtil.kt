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

import android.content.Context
import android.text.TextUtils
import org.litepal.LitePalApplication

object SharedUtil {
    private const val VERSION = "litepal_version"
    private const val LITEPAL_PREPS = "litepal_prefs"

    @JvmStatic
    fun updateVersion(extraKeyName: String?, newVersion: Int) {
        val editor = LitePalApplication.getContext()
            .getSharedPreferences(LITEPAL_PREPS, Context.MODE_PRIVATE)
            .edit()
        val key = buildVersionKey(extraKeyName)
        editor.putInt(key, newVersion)
        editor.apply()
    }

    @JvmStatic
    fun getLastVersion(extraKeyName: String?): Int {
        val pref = LitePalApplication.getContext()
            .getSharedPreferences(LITEPAL_PREPS, Context.MODE_PRIVATE)
        val key = buildVersionKey(extraKeyName)
        return pref.getInt(key, 0)
    }

    @JvmStatic
    fun removeVersion(extraKeyName: String?) {
        val editor = LitePalApplication.getContext()
            .getSharedPreferences(LITEPAL_PREPS, Context.MODE_PRIVATE)
            .edit()
        val key = buildVersionKey(extraKeyName)
        editor.remove(key)
        editor.apply()
    }

    private fun buildVersionKey(extraKeyName: String?): String {
        if (TextUtils.isEmpty(extraKeyName)) {
            return VERSION
        }
        var keyName = extraKeyName!!
        if (keyName.endsWith(Const.Config.DB_NAME_SUFFIX)) {
            keyName = keyName.replace(Const.Config.DB_NAME_SUFFIX, "")
        }
        return "${VERSION}_${keyName}"
    }
}
