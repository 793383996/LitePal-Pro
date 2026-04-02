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
import android.database.Cursor
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.litepal.litepalsample.R
import org.litepal.litepalsample.adapter.DataArrayAdapter
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.util.DbTaskExecutor
import org.litepal.tablemanager.Connector

class SaveSampleActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var singerNameEdit: EditText
    private lateinit var singerAgeEdit: EditText
    private lateinit var singerGenderEdit: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var dataListView: ListView
    private lateinit var adapter: DataArrayAdapter
    private val list: MutableList<List<String>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.save_sample_layout)
        progressBar = findViewById(R.id.progress_bar)
        singerNameEdit = findViewById(R.id.singer_name_edit)
        singerAgeEdit = findViewById(R.id.singer_age_edit)
        singerGenderEdit = findViewById(R.id.singer_gender_edit)
        val saveBtn = findViewById<Button>(R.id.save_btn)
        dataListView = findViewById(R.id.data_list_view)
        saveBtn.setOnClickListener(this)
        adapter = DataArrayAdapter(this, 0, list)
        dataListView.adapter = adapter
        populateDataFromDB()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.save_btn -> {
                try {
                    val name = singerNameEdit.text.toString()
                    val age = singerAgeEdit.text.toString().toInt()
                    val isMale = singerGenderEdit.text.toString().toBoolean()
                    v.isEnabled = false
                    DbTaskExecutor.run(
                        task = {
                            val singer = Singer()
                            singer.name = name
                            singer.age = age
                            singer.isMale = isMale
                            singer.save()
                            listOf(
                                singer.id.toString(),
                                singer.name.orEmpty(),
                                singer.age.toString(),
                                if (singer.isMale) "1" else "0"
                            )
                        },
                        onSuccess = { row ->
                            refreshListView(row)
                            v.isEnabled = true
                        },
                        onError = {
                            v.isEnabled = true
                            Toast.makeText(
                                this,
                                getString(R.string.error_param_is_not_valid),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                } catch (_: Exception) {
                    Toast.makeText(
                        this,
                        getString(R.string.error_param_is_not_valid),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun populateDataFromDB() {
        progressBar.visibility = View.VISIBLE
        DbTaskExecutor.run(
            task = {
                val snapshot = mutableListOf<List<String>>()
                snapshot.add(listOf("id", "name", "age", "ismale"))
                var cursor: Cursor? = null
                try {
                    cursor = Connector.getDatabase().rawQuery(
                        "select * from singer order by id",
                        null
                    )
                    if (cursor.moveToFirst()) {
                        do {
                            val id = cursor.getLong(cursor.getColumnIndex("id"))
                            val name = cursor.getString(cursor.getColumnIndex("name"))
                            val age = cursor.getInt(cursor.getColumnIndex("age"))
                            val isMale = cursor.getInt(cursor.getColumnIndex("ismale"))
                            snapshot.add(
                                listOf(
                                    id.toString(),
                                    name,
                                    age.toString(),
                                    isMale.toString()
                                )
                            )
                        } while (cursor.moveToNext())
                    }
                } finally {
                    cursor?.close()
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

    private fun refreshListView(row: List<String>) {
        list.add(row)
        adapter.notifyDataSetChanged()
        dataListView.setSelection(list.size)
    }

    companion object {
        @JvmStatic
        fun actionStart(context: Context) {
            val intent = Intent(context, SaveSampleActivity::class.java)
            context.startActivity(intent)
        }
    }
}
