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

class AggregateActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aggregate_layout)
        val countSampleBtn = findViewById<Button>(R.id.count_sample_btn)
        val maxSampleBtn = findViewById<Button>(R.id.max_sample_btn)
        val minSampleBtn = findViewById<Button>(R.id.min_sample_btn)
        val averageSampleBtn = findViewById<Button>(R.id.average_sample_btn)
        val sumSampleBtn = findViewById<Button>(R.id.sum_sample_btn)
        countSampleBtn.setOnClickListener(this)
        maxSampleBtn.setOnClickListener(this)
        minSampleBtn.setOnClickListener(this)
        averageSampleBtn.setOnClickListener(this)
        sumSampleBtn.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.count_sample_btn -> CountSampleActivity.actionStart(this)
            R.id.max_sample_btn -> MaxSampleActivity.actionStart(this)
            R.id.min_sample_btn -> MinSampleActivity.actionStart(this)
            R.id.average_sample_btn -> AverageSampleActivity.actionStart(this)
            R.id.sum_sample_btn -> SumSampleActivity.actionStart(this)
        }
    }

    companion object {
        @JvmStatic
        fun actionStart(context: Context) {
            val intent = Intent(context, AggregateActivity::class.java)
            context.startActivity(intent)
        }
    }
}
