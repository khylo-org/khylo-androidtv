package org.khylo.sdk

import android.content.Context
import org.khylo.sdk.android.androidDevice
import org.khylo.sdk.api.sockets.SocketConnectionFactory
import org.khylo.sdk.model.ClientInfo
import org.khylo.sdk.model.DeviceInfo
import org.khylo.sdk.model.ServerVersion
import org.khylo.sdk.api.client.ApiClientFactory
import org.khylo.sdk.api.okhttp.OkHttpFactory

public data class KhyloSdkOptions(
	public val context: Context,
	public val clientInfo: ClientInfo?,
	public val deviceInfo: DeviceInfo?,
	public val apiClientFactory: ApiClientFactory,
	public val socketConnectionFactory: SocketConnectionFactory,
	public val minimumServerVersion: ServerVersion,
) {
	public class Builder {
		public var context: Context? = null
		public var clientInfo: ClientInfo? = null
		public var deviceInfo: DeviceInfo? = null
		public var apiClientFactory: ApiClientFactory? = null
		public var socketConnectionFactory: SocketConnectionFactory? = null
		public var minimumServerVersion: ServerVersion = KhyloSdk.minimumVersion

		private val defaultClientFactory by lazy { OkHttpFactory() }

		public fun build(): KhyloSdkOptions = KhyloSdkOptions(
			context = requireNotNull(context) {
				"An Android context is required when using the khylo-sdk Android platform."
			},
			clientInfo = clientInfo,
			deviceInfo = deviceInfo ?: androidDevice(context!!),
			apiClientFactory = apiClientFactory ?: defaultClientFactory,
			socketConnectionFactory = socketConnectionFactory ?: defaultClientFactory,
			minimumServerVersion = minimumServerVersion,
		)
	}

	public companion object
}

public inline fun createKhyloSdkOptions(
	init: KhyloSdkOptions.Builder.() -> Unit,
): KhyloSdkOptions = KhyloSdkOptions.Builder().apply(init).build()
