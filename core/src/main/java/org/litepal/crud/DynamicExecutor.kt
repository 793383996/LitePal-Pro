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

package org.litepal.crud

import org.litepal.exceptions.LitePalSupportException
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object DynamicExecutor {

    private val methodCache = ConcurrentHashMap<String, Method>()
    private val fieldCache = ConcurrentHashMap<String, Field>()

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    fun send(
        `object`: Any?,
        methodName: String,
        parameters: Array<Any?>?,
        objectClass: Class<*>,
        parameterTypes: Array<Class<*>>?
    ): Any? {
        try {
            val safeParameters = parameters ?: emptyArray()
            val safeParameterTypes = parameterTypes ?: emptyArray()
            val methodKey = buildMethodKey(objectClass, methodName, safeParameterTypes)
            val method = methodCache[methodKey] ?: objectClass
                .getDeclaredMethod(methodName, *safeParameterTypes)
                .also {
                    it.isAccessible = true
                    methodCache[methodKey] = it
                }
            method.isAccessible = true
            return method.invoke(`object`, *safeParameters)
        } catch (e: NoSuchMethodException) {
            throw LitePalSupportException(
                LitePalSupportException.noSuchMethodException(objectClass.simpleName, methodName),
                e
            )
        }
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        IllegalAccessException::class
    )
    internal fun set(`object`: Any?, fieldName: String, value: Any?, objectClass: Class<*>) {
        val objectField = findField(objectClass, fieldName)
        objectField.isAccessible = true
        objectField[`object`] = value
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        IllegalAccessException::class
    )
    fun setField(`object`: Any?, fieldName: String, value: Any?, objectClass: Class<*>) {
        if (objectClass == LitePalSupport::class.java || objectClass == Any::class.java) {
            throw LitePalSupportException(
                LitePalSupportException.noSuchFieldExceptioin(objectClass.simpleName, fieldName)
            )
        }
        set(`object`, fieldName, value, objectClass)
    }

    @Throws(IllegalArgumentException::class, IllegalAccessException::class)
    fun getField(`object`: Any?, fieldName: String, objectClass: Class<*>): Any? {
        if (objectClass == LitePalSupport::class.java || objectClass == Any::class.java) {
            throw LitePalSupportException(
                LitePalSupportException.noSuchFieldExceptioin(objectClass.simpleName, fieldName)
            )
        }
        val objectField = findField(objectClass, fieldName)
        objectField.isAccessible = true
        return objectField[`object`]
    }

    private fun findField(objectClass: Class<*>, fieldName: String): Field {
        val cacheKey = "${objectClass.name}#$fieldName"
        val cached = fieldCache[cacheKey]
        if (cached != null) {
            return cached
        }
        val field = try {
            objectClass.getDeclaredField(fieldName)
        } catch (_: NoSuchFieldException) {
            val superClass = objectClass.superclass
            if (superClass == LitePalSupport::class.java || superClass == Any::class.java) {
                throw LitePalSupportException(
                    LitePalSupportException.noSuchFieldExceptioin(objectClass.simpleName, fieldName)
                )
            }
            findField(superClass, fieldName)
        }
        field.isAccessible = true
        fieldCache[cacheKey] = field
        return field
    }

    private fun buildMethodKey(
        objectClass: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>
    ): String {
        if (parameterTypes.isEmpty()) {
            return "${objectClass.name}#$methodName()"
        }
        val suffix = parameterTypes.joinToString(",") { it.name }
        return "${objectClass.name}#$methodName($suffix)"
    }
}
