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

package org.litepal.tablemanager

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.litepal.LitePalApplication
import org.litepal.Operator
import org.litepal.parser.LitePalAttr
import org.litepal.util.SharedUtil

/**
 * Database helper used by LitePal.
 */
class LitePalOpenHelper(
    context: Context,
    name: String,
    factory: SQLiteDatabase.CursorFactory?,
    version: Int
) : SQLiteOpenHelper(context, name, factory, version) {

    constructor(dbName: String, version: Int) : this(
        LitePalApplication.getContext(),
        dbName,
        null,
        version
    )

    override fun onCreate(db: SQLiteDatabase) {
        Generator.create(db)
        Operator.notifyDatabaseCreated()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Generator.upgrade(db)
        SharedUtil.updateVersion(LitePalAttr.getInstance().extraKeyName, newVersion)
        Operator.notifyDatabaseUpgraded(oldVersion, newVersion)
    }
}
