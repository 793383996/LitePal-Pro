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

class DatabaseGenerateException(errorMessage: String?) : RuntimeException(errorMessage ?: "") {
    companion object {
        const val CLASS_NOT_FOUND = "can not find a class named "
        const val SQL_ERROR =
            "An exception that indicates there was an error with SQL parsing or execution. "
        const val SQL_SYNTAX_ERROR = "SQL syntax error happens while executing "
        const val TABLE_DOES_NOT_EXIST_WHEN_EXECUTING = "Table doesn't exist when executing "
        const val TABLE_DOES_NOT_EXIST = "Table doesn't exist with the name of "
        const val EXTERNAL_STORAGE_PERMISSION_DENIED =
            "You don't have permission to access database at %1\$s. Make sure you handled WRITE_EXTERNAL_STORAGE runtime permission correctly."
        const val UNSAFE_MIGRATION =
            "Unsafe schema migration detected. LitePal 4.0 prevents implicit data cleanup for unique/not-null upgrades. "
    }
}
