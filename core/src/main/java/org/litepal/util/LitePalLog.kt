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

import android.util.Log

object LitePalLog {
    const val DEBUG = 2
    const val WARN = 4
    const val ERROR = 5

    @JvmField
    var level: Int = ERROR

    @JvmStatic
    fun d(tagName: String, message: String) {
        if (level <= DEBUG) {
            Log.d(tagName, message)
        }
    }

    @JvmStatic
    fun w(tagName: String, message: String) {
        if (level <= WARN) {
            Log.w(tagName, message)
        }
    }

    @JvmStatic
    fun e(tagName: String, e: Exception) {
        if (level <= ERROR) {
            Log.e(tagName, e.message, e)
        }
    }

    @JvmStatic
    fun e(tagName: String, message: String) {
        if (level <= ERROR) {
            Log.e(tagName, message)
        }
    }

    @JvmStatic
    fun e(tagName: String, message: String, throwable: Throwable) {
        if (level <= ERROR) {
            Log.e(tagName, message, throwable)
        }
    }
}
