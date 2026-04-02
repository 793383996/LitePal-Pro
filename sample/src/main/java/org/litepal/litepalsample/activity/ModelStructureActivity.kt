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
import org.litepal.util.BaseUtility
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class ModelStructureActivity : AppCompatActivity() {

    private lateinit var className: String
    private val list: MutableList<Field> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.model_structure_layout)
        className = intent.getStringExtra(CLASS_NAME).orEmpty()
        val modelStructureListView = findViewById<ListView>(R.id.model_structure_listview)
        analyzeModelStructure()
        val adapter = MyArrayAdapter(this, 0, list)
        modelStructureListView.adapter = adapter
    }

    private fun analyzeModelStructure() {
        val dynamicClass = try {
            Class.forName(className)
        } catch (_: ClassNotFoundException) {
            null
        } ?: return
        val fields = dynamicClass.declaredFields
        for (field in fields) {
            val modifiers = field.modifiers
            if (Modifier.isPrivate(modifiers) && !Modifier.isStatic(modifiers)) {
                val fieldType = field.type.name
                if (BaseUtility.isFieldTypeSupported(fieldType)) {
                    list.add(field)
                }
            }
        }
    }

    private class MyArrayAdapter(
        context: Context,
        textViewResourceId: Int,
        objects: List<Field>
    ) : ArrayAdapter<Field>(context, textViewResourceId, objects) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.model_structure_item, null)
            val field = getItem(position) ?: return view
            val text1 = view.findViewById<TextView>(R.id.text_1)
            text1.text = field.name
            val text2 = view.findViewById<TextView>(R.id.text_2)
            text2.text = field.type.name
            return view
        }
    }

    companion object {
        private const val CLASS_NAME = "class_name"

        @JvmStatic
        fun actionStart(context: Context, className: String) {
            val intent = Intent(context, ModelStructureActivity::class.java)
            intent.putExtra(CLASS_NAME, className)
            context.startActivity(intent)
        }
    }
}
