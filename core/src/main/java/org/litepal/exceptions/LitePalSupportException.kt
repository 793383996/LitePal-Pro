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

package org.litepal.exceptions

class LitePalSupportException : DataSupportException {
    constructor(errorMessage: String?) : super(errorMessage ?: "")
    constructor(errorMessage: String?, throwable: Throwable) : super(errorMessage ?: "", throwable)

    companion object {
        const val ID_TYPE_INVALID_EXCEPTION =
            "id type is not supported. Only int or long is acceptable for id"
        const val MODEL_IS_NOT_AN_INSTANCE_OF_LITE_PAL_SUPPORT =
            " should be inherited from LitePalSupport"
        const val WRONG_FIELD_TYPE_FOR_ASSOCIATIONS =
            "The field to declare many2one or many2many associations should be List or Set."
        const val SAVE_FAILED = "Save current model failed."
        const val INSTANTIATION_EXCEPTION = " needs a default constructor."
        const val UPDATE_CONDITIONS_EXCEPTION = "The parameters in conditions are incorrect."

        @JvmStatic
        fun noSuchMethodException(className: String, methodName: String): String {
            return "The $methodName method in $className class is necessary which does not exist."
        }

        @JvmStatic
        fun noSuchFieldExceptioin(className: String, fieldName: String): String {
            return "The $fieldName field in $className class is necessary which does not exist."
        }
    }
}
