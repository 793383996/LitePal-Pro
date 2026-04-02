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

package org.litepal.litepalsample.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.litepal.litepalsample.R

class ManageTablesActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.manage_tables_layout)
        val currentModelStructureBtn = findViewById<Button>(R.id.current_model_structure_btn)
        val operateDatabaseBtn = findViewById<Button>(R.id.operate_database_btn)
        currentModelStructureBtn.setOnClickListener(this)
        operateDatabaseBtn.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.current_model_structure_btn -> ModelListActivity.actionStart(this)
            R.id.operate_database_btn -> TableListActivity.actionStart(this)
        }
    }

    companion object {
        @JvmStatic
        fun actionStart(context: Context) {
            val intent = Intent(context, ManageTablesActivity::class.java)
            context.startActivity(intent)
        }
    }
}
