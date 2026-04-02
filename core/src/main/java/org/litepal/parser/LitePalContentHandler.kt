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

import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

class LitePalContentHandler : DefaultHandler() {

    private lateinit var litePalAttr: LitePalAttr

    @Throws(SAXException::class)
    override fun characters(ch: CharArray, start: Int, length: Int) {
        // Keep compatibility with previous behavior: value comes from attributes.
    }

    @Throws(SAXException::class)
    override fun endDocument() {
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String?, localName: String?, qName: String?) {
    }

    @Throws(SAXException::class)
    override fun startDocument() {
        litePalAttr = LitePalAttr.getInstance()
        litePalAttr.getClassNames().clear()
    }

    @Throws(SAXException::class)
    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        val safeLocalName = localName ?: return
        if (LitePalParser.NODE_DB_NAME.equals(safeLocalName, ignoreCase = true)) {
            for (i in 0 until attributes.length) {
                if (LitePalParser.ATTR_VALUE.equals(attributes.getLocalName(i), ignoreCase = true)) {
                    litePalAttr.dbName = attributes.getValue(i).trim()
                }
            }
        } else if (LitePalParser.NODE_VERSION.equals(safeLocalName, ignoreCase = true)) {
            for (i in 0 until attributes.length) {
                if (LitePalParser.ATTR_VALUE.equals(attributes.getLocalName(i), ignoreCase = true)) {
                    litePalAttr.version = attributes.getValue(i).trim().toInt()
                }
            }
        } else if (LitePalParser.NODE_MAPPING.equals(safeLocalName, ignoreCase = true)) {
            for (i in 0 until attributes.length) {
                if (LitePalParser.ATTR_CLASS.equals(attributes.getLocalName(i), ignoreCase = true)) {
                    litePalAttr.addClassName(attributes.getValue(i).trim())
                }
            }
        } else if (LitePalParser.NODE_CASES.equals(safeLocalName, ignoreCase = true)) {
            for (i in 0 until attributes.length) {
                if (LitePalParser.ATTR_VALUE.equals(attributes.getLocalName(i), ignoreCase = true)) {
                    litePalAttr.cases = attributes.getValue(i).trim()
                }
            }
        } else if (LitePalParser.NODE_STORAGE.equals(safeLocalName, ignoreCase = true)) {
            for (i in 0 until attributes.length) {
                if (LitePalParser.ATTR_VALUE.equals(attributes.getLocalName(i), ignoreCase = true)) {
                    litePalAttr.storage = attributes.getValue(i).trim()
                }
            }
        }
    }
}
