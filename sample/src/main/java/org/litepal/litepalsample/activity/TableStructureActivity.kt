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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.litepal.litepalsample.R
import org.litepal.litepalsample.util.DbTaskExecutor
import org.litepal.tablemanager.Connector
import org.litepal.tablemanager.model.ColumnModel
import org.litepal.util.DBUtility

class TableStructureActivity : AppCompatActivity() {

    private lateinit var tableName: String
    private lateinit var adapter: MyArrayAdapter
    private val list: MutableList<ColumnModel> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.table_structure_layout)
        tableName = intent.getStringExtra(TABLE_NAME).orEmpty()
        val tableStructureListView = findViewById<ListView>(R.id.table_structure_listview)
        adapter = MyArrayAdapter(this, 0, list)
        tableStructureListView.adapter = adapter
        analyzeTableStructure()
    }

    private fun analyzeTableStructure() {
        DbTaskExecutor.run(
            task = {
                DBUtility.findPragmaTableInfo(tableName, Connector.getDatabase()).getColumnModels()
            },
            onSuccess = { columns ->
                list.clear()
                list.addAll(columns)
                adapter.notifyDataSetChanged()
            }
        )
    }

    private class MyArrayAdapter(
        context: Context,
        textViewResourceId: Int,
        objects: List<ColumnModel>
    ) : ArrayAdapter<ColumnModel>(context, textViewResourceId, objects) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.table_structure_item, null)
            val columnModel = getItem(position) ?: return view
            val text1 = view.findViewById<TextView>(R.id.text_1)
            text1.text = columnModel.getColumnName()
            val text2 = view.findViewById<TextView>(R.id.text_2)
            text2.text = columnModel.getColumnType()
            val text3 = view.findViewById<TextView>(R.id.text_3)
            text3.text = columnModel.isNullable().toString()
            val text4 = view.findViewById<TextView>(R.id.text_4)
            text4.text = columnModel.isUnique().toString()
            val text5 = view.findViewById<TextView>(R.id.text_5)
            text5.text = columnModel.getDefaultValue()
            val text6 = view.findViewById<TextView>(R.id.text_6)
            text6.text = columnModel.hasIndex().toString()
            return view
        }
    }

    companion object {
        private const val TABLE_NAME = "table_name"

        @JvmStatic
        fun actionStart(context: Context, tableName: String) {
            val intent = Intent(context, TableStructureActivity::class.java)
            intent.putExtra(TABLE_NAME, tableName)
            context.startActivity(intent)
        }
    }
}
