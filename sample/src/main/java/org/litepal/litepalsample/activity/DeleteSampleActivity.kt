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
import org.litepal.LitePal
import org.litepal.litepalsample.R
import org.litepal.litepalsample.adapter.DataArrayAdapter
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.util.DbTaskExecutor
import org.litepal.tablemanager.Connector

class DeleteSampleActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var singerIdEdit: EditText
    private lateinit var nameToDeleteEdit: EditText
    private lateinit var ageToDeleteEdit: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: DataArrayAdapter
    private val list: MutableList<List<String>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.delete_sample_layout)
        progressBar = findViewById(R.id.progress_bar)
        singerIdEdit = findViewById(R.id.singer_id_edit)
        nameToDeleteEdit = findViewById(R.id.name_to_delete)
        ageToDeleteEdit = findViewById(R.id.age_to_delete)
        val deleteBtn1 = findViewById<Button>(R.id.delete_btn1)
        val deleteBtn2 = findViewById<Button>(R.id.delete_btn2)
        val dataListView = findViewById<ListView>(R.id.data_list_view)
        deleteBtn1.setOnClickListener(this)
        deleteBtn2.setOnClickListener(this)
        adapter = DataArrayAdapter(this, 0, list)
        dataListView.adapter = adapter
        populateDataFromDB()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.delete_btn1 -> {
                try {
                    val singerId = singerIdEdit.text.toString().toLong()
                    v.isEnabled = false
                    DbTaskExecutor.run(
                        task = {
                            LitePal.delete(Singer::class.java, singerId)
                        },
                        onSuccess = { rowsAffected ->
                            Toast.makeText(
                                this,
                                getString(R.string.number_of_rows_affected, rowsAffected.toString()),
                                Toast.LENGTH_SHORT
                            ).show()
                            populateDataFromDB()
                            v.isEnabled = true
                        },
                        onError = {
                            Toast.makeText(
                                this,
                                getString(R.string.error_param_is_not_valid),
                                Toast.LENGTH_SHORT
                            ).show()
                            v.isEnabled = true
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

            R.id.delete_btn2 -> {
                try {
                    val name = nameToDeleteEdit.text.toString()
                    val age = ageToDeleteEdit.text.toString()
                    v.isEnabled = false
                    DbTaskExecutor.run(
                        task = {
                            LitePal.deleteAll(
                                Singer::class.java,
                                "name=? and age=?",
                                name,
                                age
                            )
                        },
                        onSuccess = { rowsAffected ->
                            Toast.makeText(
                                this,
                                getString(R.string.number_of_rows_affected, rowsAffected.toString()),
                                Toast.LENGTH_SHORT
                            ).show()
                            populateDataFromDB()
                            v.isEnabled = true
                        },
                        onError = {
                            Toast.makeText(
                                this,
                                getString(R.string.error_param_is_not_valid),
                                Toast.LENGTH_SHORT
                            ).show()
                            v.isEnabled = true
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

    companion object {
        @JvmStatic
        fun actionStart(context: Context) {
            val intent = Intent(context, DeleteSampleActivity::class.java)
            context.startActivity(intent)
        }
    }
}
