package org.khylo.tv.core.ui

import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.khylo.tv.core.data.repository.ItemRepository
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.extensions.itemsApi
import org.koin.java.KoinJavaComponent

fun ItemListView.refresh() {
	val api by KoinJavaComponent.inject<ApiClient>(ApiClient::class.java)

	findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
		val response = withContext(Dispatchers.IO) {
			api.itemsApi.getItems(
				ids = mItemIds,
				fields = ItemRepository.itemFields
			).content
		}

		response.items?.forEachIndexed { index, item ->
			val view = mList.getChildAt(index)
			if (view is ItemRowView) view.setItem(item, index)
		}
	}
}
