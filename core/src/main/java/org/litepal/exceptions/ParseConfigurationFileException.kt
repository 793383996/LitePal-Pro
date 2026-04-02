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

class ParseConfigurationFileException(errorMessage: String) : RuntimeException(errorMessage) {
    companion object {
        const val CAN_NOT_FIND_LITEPAL_FILE =
            "litepal.xml file is missing. Please ensure it under assets folder."
        const val FILE_FORMAT_IS_NOT_CORRECT =
            "can not parse the litepal.xml, check if it's in correct format"
        const val PARSE_CONFIG_FAILED = "parse configuration is failed"
        const val IO_EXCEPTION = "IO exception happened"
    }
}
