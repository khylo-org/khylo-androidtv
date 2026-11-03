package org.khylo.tv.core.ui.itemhandling

import android.content.Context
import org.khylo.tv.core.constant.ImageType
import org.khylo.tv.core.util.apiclient.primaryImage
import org.khylo.sdk.model.api.BaseItemPerson

class BaseItemPersonBaseRowItem(
	val person: BaseItemPerson,
) : BaseRowItem(
	baseRowType = BaseRowType.Person,
	staticHeight = true,
) {
	override fun getImage(imageType: ImageType) = person.primaryImage
	override val itemId get() = person.id
	override fun getFullName(context: Context) = person.name
	override fun getName(context: Context) = person.name
	override fun getSubText(context: Context) = person.role
}
