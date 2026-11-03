package org.khylo.tv.core.util.sdk

import org.khylo.sdk.api.client.ApiClient


/**
 * Check if the [baseUrl] and [accessToken] are not null.
 */
val ApiClient.isUsable
	get() = baseUrl != null && accessToken != null
