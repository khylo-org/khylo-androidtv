package org.khylo.sdk.api.client

import org.khylo.sdk.api.sockets.SocketConnectionFactory
import org.khylo.sdk.model.ClientInfo
import org.khylo.sdk.model.DeviceInfo

public fun interface ApiClientFactory {
	@Suppress("LongParameterList")
	public fun create(
		baseUrl: String?,
		accessToken: String?,
		clientInfo: ClientInfo,
		deviceInfo: DeviceInfo,
		httpClientOptions: HttpClientOptions,
		socketConnectionFactory: SocketConnectionFactory,
	): ApiClient
}
