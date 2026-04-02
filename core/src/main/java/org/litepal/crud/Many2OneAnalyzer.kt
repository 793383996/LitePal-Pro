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

import org.litepal.crud.model.AssociationsInfo
import org.litepal.util.DBUtility
import java.lang.reflect.InvocationTargetException

internal class Many2OneAnalyzer : AssociationsAnalyzer() {

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    fun analyze(baseObj: LitePalSupport, associationInfo: AssociationsInfo) {
        if (baseObj.getClassName() == associationInfo.getClassHoldsForeignKey()) {
            analyzeManySide(baseObj, associationInfo)
        } else {
            analyzeOneSide(baseObj, associationInfo)
        }
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    private fun analyzeManySide(baseObj: LitePalSupport, associationInfo: AssociationsInfo) {
        val associatedModel = getAssociatedModel(baseObj, associationInfo)
        if (associatedModel != null) {
            val reverseField = associationInfo.getAssociateSelfFromOtherModel() ?: return
            val tempCollection = getReverseAssociatedModels(associatedModel, associationInfo)
            val reverseAssociatedModels = checkAssociatedModelCollection(
                tempCollection,
                reverseField
            )
            setReverseAssociatedModels(associatedModel, associationInfo, reverseAssociatedModels)
            dealAssociatedModelOnManySide(reverseAssociatedModels, baseObj, associatedModel)
        } else {
            mightClearFKValue(baseObj, associationInfo)
        }
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    private fun analyzeOneSide(baseObj: LitePalSupport, associationInfo: AssociationsInfo) {
        val associatedModels = getAssociatedModels(baseObj, associationInfo)
        if (associatedModels == null || associatedModels.isEmpty()) {
            val tableName = DBUtility.getTableNameByClassName(associationInfo.getAssociatedClassName()).orEmpty()
            baseObj.addAssociatedTableNameToClearFK(tableName)
            return
        }
        for (associatedModel in associatedModels) {
            buildBidirectionalAssociations(baseObj, associatedModel, associationInfo)
            dealAssociatedModelOnOneSide(baseObj, associatedModel)
        }
    }

    private fun dealAssociatedModelOnManySide(
        associatedModels: MutableCollection<LitePalSupport>,
        baseObj: LitePalSupport,
        associatedModel: LitePalSupport
    ) {
        if (!associatedModels.contains(baseObj)) {
            associatedModels.add(baseObj)
        }
        if (associatedModel.isSaved()) {
            baseObj.addAssociatedModelWithoutFK(associatedModel.getTableName(), associatedModel.getBaseObjId())
        }
    }

    private fun dealAssociatedModelOnOneSide(baseObj: LitePalSupport, associatedModel: LitePalSupport) {
        dealsAssociationsOnTheSideWithoutFK(baseObj, associatedModel)
    }
}
