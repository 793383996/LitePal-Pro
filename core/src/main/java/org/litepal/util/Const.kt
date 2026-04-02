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

object Const {
    object Model {
        const val ONE_TO_ONE = 1
        const val MANY_TO_ONE = 2
        const val MANY_TO_MANY = 3
    }

    object Config {
        const val DB_NAME_SUFFIX = ".db"
        const val CASES_UPPER = "upper"
        const val CASES_LOWER = "lower"
        const val CASES_KEEP = "keep"
        const val CONFIGURATION_FILE_NAME = "litepal.xml"
    }

    object TableSchema {
        const val TABLE_NAME = "table_schema"
        const val COLUMN_NAME = "name"
        const val COLUMN_TYPE = "type"
        const val NORMAL_TABLE = 0
        const val INTERMEDIATE_JOIN_TABLE = 1
        const val GENERIC_TABLE = 2
    }
}
