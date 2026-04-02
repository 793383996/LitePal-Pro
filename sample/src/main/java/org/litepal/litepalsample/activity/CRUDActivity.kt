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

class CRUDActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crud_layout)
        val saveSampleBtn = findViewById<Button>(R.id.save_sample_btn)
        val updateSampleBtn = findViewById<Button>(R.id.update_sample_btn)
        val deleteSampleBtn = findViewById<Button>(R.id.delete_sample_btn)
        val querySampleBtn = findViewById<Button>(R.id.query_sample_btn)
        saveSampleBtn.setOnClickListener(this)
        updateSampleBtn.setOnClickListener(this)
        deleteSampleBtn.setOnClickListener(this)
        querySampleBtn.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.save_sample_btn -> SaveSampleActivity.actionStart(this)
            R.id.update_sample_btn -> UpdateSampleActivity.actionStart(this)
            R.id.delete_sample_btn -> DeleteSampleActivity.actionStart(this)
            R.id.query_sample_btn -> QuerySampleActivity.actionStart(this)
        }
    }

    companion object {
        @JvmStatic
        fun actionStart(context: Context) {
            val intent = Intent(context, CRUDActivity::class.java)
            context.startActivity(intent)
        }
    }
}
