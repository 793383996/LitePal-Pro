/*
 * Copyright (C)  Tony Green, Litepal Framework Open Source Project
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

package org.litepal.litepalsample.model

import org.litepal.annotation.Column
import org.litepal.crud.LitePalSupport

class Song : LitePalSupport() {
    var id: Long = 0

    @Column(index = true)
    var name: String? = null

    @Column(unique = true, index = true)
    var lyric: String? = null

    var duration: String? = null
    var album: Album? = null
}
