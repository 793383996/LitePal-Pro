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
import android.widget.ListView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import org.litepal.litepalsample.R
import org.litepal.litepalsample.adapter.StringArrayAdapter
import org.litepal.litepalsample.util.DbTaskExecutor
import org.litepal.tablemanager.Connector
import org.litepal.util.DBUtility

class TableListActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: StringArrayAdapter
    private val list: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.table_list_layout)
        progressBar = findViewById(R.id.progress_bar)
        val tableListView = findViewById<ListView>(R.id.table_listview)
        adapter = StringArrayAdapter(this, 0, list)
        tableListView.adapter = adapter
        populateTables()
        tableListView.setOnItemClickListener { _, _, index, _ ->
            TableStructureActivity.actionStart(this, list[index])
        }
    }

    private fun populateTables() {
        progressBar.visibility = View.VISIBLE
        DbTaskExecutor.run(
            task = {
                val snapshot = mutableListOf<String>()
                val tables = DBUtility.findAllTableNames(Connector.getDatabase())
                for (table in tables) {
                    if (table.equals("android_metadata", ignoreCase = true)
                        || table.equals("sqlite_sequence", ignoreCase = true)
                        || table.equals("table_schema", ignoreCase = true)
                    ) {
                        continue
                    }
                    snapshot.add(table)
                }
                snapshot
            },
            onSuccess = { snapshot ->
                list.clear()
                list.addAll(snapshot)
                progressBar.visibility = View.GONE
                adapter.notifyDataSetChanged()
            },
            onError = {
                progressBar.visibility = View.GONE
                adapter.notifyDataSetChanged()
            }
        )
    }

    companion object {
        @JvmStatic
        fun actionStart(context: Context) {
            val intent = Intent(context, TableListActivity::class.java)
            context.startActivity(intent)
        }
    }
}
