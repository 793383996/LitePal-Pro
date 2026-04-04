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

package org.litepal.crud.model

import java.util.Locale

class AssociationsInfo {
    private var selfClassName: String? = null
    private var associatedClassName: String? = null
    private var classHoldsForeignKey: String? = null
    private var associateOtherModelFromSelf: String? = null
    private var associateSelfFromOtherModel: String? = null
    private var associateOtherModelCollectionType: String? = null
    private var associateSelfCollectionType: String? = null
    private var associationType: Int = 0

    fun getSelfClassName(): String? = selfClassName

    fun setSelfClassName(selfClassName: String?) {
        this.selfClassName = selfClassName
    }

    fun getAssociatedClassName(): String? = associatedClassName

    fun setAssociatedClassName(associatedClassName: String?) {
        this.associatedClassName = associatedClassName
    }

    fun getClassHoldsForeignKey(): String? = classHoldsForeignKey

    fun setClassHoldsForeignKey(classHoldsForeignKey: String?) {
        this.classHoldsForeignKey = classHoldsForeignKey
    }

    fun getAssociateOtherModelFromSelf(): String? = associateOtherModelFromSelf

    fun setAssociateOtherModelFromSelf(associateOtherModelFromSelf: String?) {
        this.associateOtherModelFromSelf = associateOtherModelFromSelf
    }

    fun getAssociateSelfFromOtherModel(): String? = associateSelfFromOtherModel

    fun setAssociateSelfFromOtherModel(associateSelfFromOtherModel: String?) {
        this.associateSelfFromOtherModel = associateSelfFromOtherModel
    }

    fun getAssociateOtherModelCollectionType(): String? = associateOtherModelCollectionType

    fun setAssociateOtherModelCollectionType(type: String?) {
        associateOtherModelCollectionType = normalizeCollectionType(type)
    }

    fun getAssociateSelfCollectionType(): String? = associateSelfCollectionType

    fun setAssociateSelfCollectionType(type: String?) {
        associateSelfCollectionType = normalizeCollectionType(type)
    }

    fun getAssociationType(): Int = associationType

    fun setAssociationType(associationType: Int) {
        this.associationType = associationType
    }

    fun isAssociateOtherModelCollection(): Boolean = associateOtherModelCollectionType != null

    fun isAssociateSelfCollection(): Boolean = associateSelfCollectionType != null

    override fun equals(other: Any?): Boolean {
        if (other !is AssociationsInfo) return false
        val otherClassHoldsForeignKey = other.getClassHoldsForeignKey()
        if (otherClassHoldsForeignKey == null || classHoldsForeignKey == null) return false
        if (other.getAssociationType() != associationType) return false
        if (otherClassHoldsForeignKey != classHoldsForeignKey) return false
        val otherSelf = other.getSelfClassName()
        val otherAssociated = other.getAssociatedClassName()
        if (otherSelf == null || otherAssociated == null || selfClassName == null || associatedClassName == null) {
            return false
        }
        if (otherSelf == selfClassName && otherAssociated == associatedClassName) return true
        return otherSelf == associatedClassName && otherAssociated == selfClassName
    }

    override fun hashCode(): Int {
        val localSelf = selfClassName ?: return 0
        val localAssociated = associatedClassName ?: return 0
        val localHoldsFk = classHoldsForeignKey ?: return 0
        val normalizedSelf = localSelf.lowercase(Locale.US)
        val normalizedAssociated = localAssociated.lowercase(Locale.US)
        val first = if (normalizedSelf <= normalizedAssociated) {
            normalizedSelf
        } else {
            normalizedAssociated
        }
        val second = if (normalizedSelf <= normalizedAssociated) {
            normalizedAssociated
        } else {
            normalizedSelf
        }
        var result = associationType
        result = 31 * result + localHoldsFk.lowercase(Locale.US).hashCode()
        result = 31 * result + first.hashCode()
        result = 31 * result + second.hashCode()
        return result
    }

    private fun normalizeCollectionType(type: String?): String? {
        val normalized = type?.trim()?.uppercase(Locale.US) ?: return null
        return when (normalized) {
            "LIST", "SET" -> normalized
            else -> null
        }
    }
}
