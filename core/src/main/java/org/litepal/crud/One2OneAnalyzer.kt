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

class One2OneAnalyzer : AssociationsAnalyzer() {

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    fun analyze(baseObj: LitePalSupport, associationInfo: AssociationsInfo) {
        val associatedModel = getAssociatedModel(baseObj, associationInfo)
        if (associatedModel != null) {
            buildBidirectionalAssociations(baseObj, associatedModel, associationInfo)
            dealAssociatedModel(baseObj, associatedModel, associationInfo)
        } else {
            val tableName = DBUtility.getTableNameByClassName(associationInfo.getAssociatedClassName()).orEmpty()
            baseObj.addAssociatedTableNameToClearFK(tableName)
        }
    }

    private fun dealAssociatedModel(
        baseObj: LitePalSupport,
        associatedModel: LitePalSupport,
        associationInfo: AssociationsInfo
    ) {
        if (associationInfo.getAssociateSelfFromOtherModel() != null) {
            bidirectionalCondition(baseObj, associatedModel)
        } else {
            unidirectionalCondition(baseObj, associatedModel)
        }
    }

    private fun bidirectionalCondition(baseObj: LitePalSupport, associatedModel: LitePalSupport) {
        if (associatedModel.isSaved()) {
            baseObj.addAssociatedModelWithFK(associatedModel.getTableName(), associatedModel.getBaseObjId())
            baseObj.addAssociatedModelWithoutFK(associatedModel.getTableName(), associatedModel.getBaseObjId())
        }
    }

    private fun unidirectionalCondition(baseObj: LitePalSupport, associatedModel: LitePalSupport) {
        dealsAssociationsOnTheSideWithoutFK(baseObj, associatedModel)
    }
}
