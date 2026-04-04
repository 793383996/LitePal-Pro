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
import org.litepal.exceptions.LitePalSupportException
import org.litepal.util.DBUtility

abstract class AssociationsAnalyzer : DataHandler() {

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    protected fun getReverseAssociatedModels(
        associatedModel: LitePalSupport,
        associationInfo: AssociationsInfo
    ): Collection<LitePalSupport>? {
        @Suppress("UNCHECKED_CAST")
        return getFieldValue(
            associatedModel,
            associationInfo.getAssociateSelfFromOtherModel()
        ) as Collection<LitePalSupport>?
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    protected fun setReverseAssociatedModels(
        associatedModel: LitePalSupport,
        associationInfo: AssociationsInfo,
        associatedModelCollection: Collection<LitePalSupport>
    ) {
        setFieldValue(
            associatedModel,
            associationInfo.getAssociateSelfFromOtherModel(),
            associatedModelCollection
        )
    }

    protected fun checkAssociatedModelCollection(
        associatedModelCollection: Collection<LitePalSupport>?,
        collectionType: String?
    ): MutableCollection<LitePalSupport> {
        val collection: MutableCollection<LitePalSupport> = when {
            isList(collectionType) -> ArrayList<LitePalSupport>()
            isSet(collectionType) -> HashSet<LitePalSupport>()
            else -> throw LitePalSupportException(LitePalSupportException.WRONG_FIELD_TYPE_FOR_ASSOCIATIONS)
        }
        if (associatedModelCollection != null) {
            collection.addAll(associatedModelCollection)
        }
        return collection
    }

    @Throws(
        SecurityException::class,
        IllegalArgumentException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    protected fun buildBidirectionalAssociations(
        baseObj: LitePalSupport,
        associatedModel: LitePalSupport,
        associationInfo: AssociationsInfo
    ) {
        setFieldValue(
            associatedModel,
            associationInfo.getAssociateSelfFromOtherModel(),
            baseObj
        )
    }

    protected fun dealsAssociationsOnTheSideWithoutFK(
        baseObj: LitePalSupport,
        associatedModel: LitePalSupport?
    ) {
        if (associatedModel != null) {
            if (associatedModel.isSaved()) {
                baseObj.addAssociatedModelWithFK(
                    associatedModel.getTableName(),
                    associatedModel.getBaseObjId()
                )
            } else if (baseObj.isSaved()) {
                associatedModel.addAssociatedModelWithoutFK(
                    baseObj.getTableName(),
                    baseObj.getBaseObjId()
                )
            }
        }
    }

    protected fun mightClearFKValue(baseObj: LitePalSupport, associationInfo: AssociationsInfo) {
        baseObj.addFKNameToClearSelf(getForeignKeyName(associationInfo))
    }

    private fun getForeignKeyName(associationInfo: AssociationsInfo): String {
        return getForeignKeyColumnName(
            DBUtility.getTableNameByClassName(associationInfo.getAssociatedClassName())
        ).orEmpty()
    }
}
