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
import android.content.res.AssetManager
import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import org.litepal.LitePalApplication
import org.litepal.exceptions.ParseConfigurationFileException
import org.litepal.litepalsample.R
import org.litepal.litepalsample.adapter.StringArrayAdapter
import org.litepal.util.Const
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream

class ModelListActivity : AppCompatActivity() {

    private val list: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.model_list_layout)
        val modelListView = findViewById<ListView>(R.id.model_listview)
        populateMappingClasses()
        val adapter = StringArrayAdapter(this, 0, list)
        modelListView.adapter = adapter
        modelListView.setOnItemClickListener { _, _, index, _ ->
            ModelStructureActivity.actionStart(this, list[index])
        }
    }

    private fun populateMappingClasses() {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val xmlPullParser = factory.newPullParser()
            xmlPullParser.setInput(getInputStream(), "UTF-8")
            var eventType = xmlPullParser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val nodeName = xmlPullParser.name
                if (eventType == XmlPullParser.START_TAG && "mapping" == nodeName) {
                    val className = xmlPullParser.getAttributeValue("", "class")
                    list.add(className)
                }
                eventType = xmlPullParser.next()
            }
        } catch (_: XmlPullParserException) {
            throw ParseConfigurationFileException(
                ParseConfigurationFileException.FILE_FORMAT_IS_NOT_CORRECT
            )
        } catch (_: IOException) {
            throw ParseConfigurationFileException(ParseConfigurationFileException.IO_EXCEPTION)
        }
    }

    @Throws(IOException::class)
    private fun getInputStream(): InputStream {
        val assetManager: AssetManager = LitePalApplication.getContext().assets
        val fileNames = assetManager.list("")
        if (fileNames != null && fileNames.isNotEmpty()) {
            for (fileName in fileNames) {
                if (Const.Config.CONFIGURATION_FILE_NAME.equals(fileName, ignoreCase = true)) {
                    return assetManager.open(fileName, AssetManager.ACCESS_BUFFER)
                }
            }
        }
        throw ParseConfigurationFileException(
            ParseConfigurationFileException.CAN_NOT_FIND_LITEPAL_FILE
        )
    }

    companion object {
        @JvmStatic
        fun actionStart(context: Context) {
            val intent = Intent(context, ModelListActivity::class.java)
            context.startActivity(intent)
        }
    }
}
