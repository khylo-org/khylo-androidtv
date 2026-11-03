package org.khylo.tv.core.ui.itemhandling

import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.khylo.tv.core.util.apiclient.Response
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.exception.ApiClientException
import org.khylo.sdk.api.client.extensions.userLibraryApi
import org.khylo.sdk.model.api.BaseItemDto
import org.koin.java.KoinJavaComponent
import java.util.UUID

object ItemLauncherHelper {
	@JvmStatic
	fun getItem(itemId: UUID, callback: Response<BaseItemDto>) {
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			val api by KoinJavaComponent.inject<ApiClient>(ApiClient::class.java)

			try {
				val response = withContext(Dispatchers.IO) {
					api.userLibraryApi.getItem(itemId = itemId).content
				}
				callback.onResponse(response)
			} catch (error: ApiClientException) {
				callback.onError(error)
			}
		}
	}
}
