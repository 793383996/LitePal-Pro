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

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.litepal.exceptions.GlobalException

/**
 * Base class of LitePal to make things easier when developers need to use context.
 */
class LitePalApplication : Application() {

    init {
        sContext = this
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @JvmField
        var sContext: Context? = null

        @JvmField
        var sHandler: Handler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun getContext(): Context {
            return sContext ?: throw GlobalException(GlobalException.APPLICATION_CONTEXT_IS_NULL)
        }
    }
}
