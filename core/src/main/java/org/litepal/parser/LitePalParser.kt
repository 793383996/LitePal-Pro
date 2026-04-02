/*
 * Copyright (C)  Tony Green, LitePal Framework Open Source Project
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

package org.litepal.parser

import android.content.res.AssetManager
import android.content.res.Resources
import org.litepal.LitePalApplication
import org.litepal.exceptions.ParseConfigurationFileException
import org.litepal.util.Const
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

class LitePalParser private constructor() {

    companion object {
        const val NODE_DB_NAME = "dbname"
        const val NODE_VERSION = "version"
        const val NODE_LIST = "list"
        const val NODE_MAPPING = "mapping"
        const val NODE_CASES = "cases"
        const val NODE_STORAGE = "storage"
        const val ATTR_VALUE = "value"
        const val ATTR_CLASS = "class"

        @Volatile
        private var parser: LitePalParser? = null

        @JvmStatic
        fun parseLitePalConfiguration(): LitePalConfig {
            val existing = parser
            val target = existing ?: synchronized(LitePalParser::class.java) {
                parser ?: LitePalParser().also { parser = it }
            }
            return target.usePullParse()
        }
    }

    @Suppress("unused")
    private fun useSAXParser() {
        try {
            val factory = SAXParserFactory.newInstance()
            val xmlReader: XMLReader = factory.newSAXParser().xmlReader
            val handler = LitePalContentHandler()
            xmlReader.contentHandler = handler
            xmlReader.parse(InputSource(getConfigInputStream()))
        } catch (e: Resources.NotFoundException) {
            throw ParseConfigurationFileException(ParseConfigurationFileException.CAN_NOT_FIND_LITEPAL_FILE)
        } catch (e: org.xml.sax.SAXException) {
            throw ParseConfigurationFileException(ParseConfigurationFileException.FILE_FORMAT_IS_NOT_CORRECT)
        } catch (e: ParserConfigurationException) {
            throw ParseConfigurationFileException(ParseConfigurationFileException.PARSE_CONFIG_FAILED)
        } catch (e: IOException) {
            throw ParseConfigurationFileException(ParseConfigurationFileException.IO_EXCEPTION)
        }
    }

    private fun usePullParse(): LitePalConfig {
        try {
            val litePalConfig = LitePalConfig()
            val factory = XmlPullParserFactory.newInstance()
            val xmlPullParser = factory.newPullParser()
            xmlPullParser.setInput(getConfigInputStream(), "UTF-8")
            var eventType = xmlPullParser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val nodeName = xmlPullParser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (NODE_DB_NAME == nodeName) {
                            litePalConfig.dbName = xmlPullParser.getAttributeValue("", ATTR_VALUE)
                        } else if (NODE_VERSION == nodeName) {
                            litePalConfig.version = xmlPullParser.getAttributeValue("", ATTR_VALUE).toInt()
                        } else if (NODE_MAPPING == nodeName) {
                            litePalConfig.addClassName(xmlPullParser.getAttributeValue("", ATTR_CLASS))
                        } else if (NODE_CASES == nodeName) {
                            litePalConfig.cases = xmlPullParser.getAttributeValue("", ATTR_VALUE)
                        } else if (NODE_STORAGE == nodeName) {
                            litePalConfig.storage = xmlPullParser.getAttributeValue("", ATTR_VALUE)
                        }
                    }
                }
                eventType = xmlPullParser.next()
            }
            return litePalConfig
        } catch (e: XmlPullParserException) {
            throw ParseConfigurationFileException(ParseConfigurationFileException.FILE_FORMAT_IS_NOT_CORRECT)
        } catch (e: IOException) {
            throw ParseConfigurationFileException(ParseConfigurationFileException.IO_EXCEPTION)
        }
    }

    @Throws(IOException::class)
    private fun getConfigInputStream(): InputStream {
        val assetManager = LitePalApplication.getContext().assets
        val fileNames = assetManager.list("")
        if (fileNames != null && fileNames.isNotEmpty()) {
            for (fileName in fileNames) {
                if (Const.Config.CONFIGURATION_FILE_NAME.equals(fileName, ignoreCase = true)) {
                    return assetManager.open(fileName, AssetManager.ACCESS_BUFFER)
                }
            }
        }
        throw ParseConfigurationFileException(ParseConfigurationFileException.CAN_NOT_FIND_LITEPAL_FILE)
    }
}
