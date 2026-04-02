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

package org.litepal.litepalsample.adapter

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import org.litepal.litepalsample.util.Utility

class DataArrayAdapter(
    context: Context,
    textViewResourceId: Int,
    objects: List<List<String>>
) : ArrayAdapter<List<String>>(context, textViewResourceId, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val dataList = getItem(position).orEmpty()
        val layout = if (convertView == null) {
            LinearLayout(context)
        } else {
            convertView as LinearLayout
        }
        layout.removeAllViews()
        val width = Utility.dp2px(context, 100f)
        val height = Utility.dp2px(context, 30f)
        for (data in dataList) {
            val params = LinearLayout.LayoutParams(width, height)
            val textView = TextView(context)
            textView.text = data
            textView.setSingleLine(true)
            textView.ellipsize = TextUtils.TruncateAt.END
            textView.gravity = Gravity.CENTER_VERTICAL
            layout.addView(textView, params)
        }
        return layout
    }
}
