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

class InvalidAttributesException(errorMessage: String) : RuntimeException(errorMessage) {
    companion object {
        const val DBNAME_IS_EMPTY_OR_NOT_DEFINED =
            "dbname is empty or not defined in litepal.xml file, or your litepal.xml file is missing."
        const val VERSION_OF_DATABASE_LESS_THAN_ONE =
            "the version of database can not be less than 1"
        const val VERSION_IS_EARLIER_THAN_CURRENT =
            "the version in litepal.xml is earlier than the current version"
        const val CASES_VALUE_IS_INVALID = " is an invalid value for <cases></cases>"
    }
}
