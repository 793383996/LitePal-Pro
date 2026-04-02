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
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.litepal.LitePal
import org.litepal.litepalsample.R
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.util.DbTaskExecutor

class CountSampleActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var ageEdit: EditText
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.count_sample_layout)
        val countBtn1 = findViewById<Button>(R.id.count_btn1)
        val countBtn2 = findViewById<Button>(R.id.count_btn2)
        ageEdit = findViewById(R.id.age_edit)
        resultText = findViewById(R.id.result_text)
        countBtn1.setOnClickListener(this)
        countBtn2.setOnClickListener(this)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.count_btn1 -> {
                DbTaskExecutor.run(
                    task = { LitePal.count(Singer::class.java) },
                    onSuccess = { result -> resultText.text = result.toString() }
                )
            }

            R.id.count_btn2 -> {
                val ageText = ageEdit.text.toString()
                DbTaskExecutor.run(
                    task = {
                        LitePal.where("age > ?", ageText).count(Singer::class.java)
                    },
                    onSuccess = { result -> resultText.text = result.toString() }
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun actionStart(context: Context) {
            val intent = Intent(context, CountSampleActivity::class.java)
            context.startActivity(intent)
        }
    }
}
