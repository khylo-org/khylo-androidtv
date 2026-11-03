package org.khylo.sdk

import org.khylo.sdk.KhyloSdk.Companion.minimumVersion
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.HttpClientOptions
import org.khylo.sdk.api.info.ApiConstants
import org.khylo.sdk.discovery.DiscoveryService
import org.khylo.sdk.model.ClientInfo
import org.khylo.sdk.model.DeviceInfo
import org.khylo.sdk.model.ServerVersion

public class KhyloSdk(
	public val options: KhyloSdkOptions,
) {
	public constructor(optionsBuilder: KhyloSdkOptions.Builder) : this(optionsBuilder.build())

	/**
	 * Get the device information set when creating the Khylo instance or null if not set.
	 */
	public val deviceInfo: DeviceInfo? get() = options.deviceInfo

	/**
	 * Get the client information set when creating the Khylo instance or null if not set.
	 */
	public val clientInfo: ClientInfo? get() = options.clientInfo

	/**
	 * Get the discovery service to help with normalizing server addresses and find servers in the local network.
	 */
	public val discovery: DiscoveryService by lazy {
		DiscoveryService(this)
	}

	/**
	 * Create a new API instance to use in API services.
	 * The [clientInfo] and [deviceInfo] parameters are required when not passed as option in [KhyloSdkOptions].
	 * The [baseUrl] is only required when HTTP calls are made.
	 *
	 * Throws an [IllegalStateException] when the client or device information is missing.
	 */
	@Suppress("LongParameterList")
	public fun createApi(
		baseUrl: String? = null,
		accessToken: String? = null,
		clientInfo: ClientInfo? = options.clientInfo,
		deviceInfo: DeviceInfo? = options.deviceInfo,
		httpClientOptions: HttpClientOptions = HttpClientOptions(),
	): ApiClient {
		checkNotNull(clientInfo) {
			"ClientInfo needs to be set when calling createApi() or by providing it when constructing the KhyloSdk instance"
		}
		checkNotNull(deviceInfo) {
			"DeviceInfo needs to be set when calling createApi() or by providing it when constructing the KhyloSdk instance"
		}

		return options.apiClientFactory.create(
			baseUrl = baseUrl,
			accessToken = accessToken,
			clientInfo = clientInfo,
			deviceInfo = deviceInfo,
			httpClientOptions = httpClientOptions,
			socketConnectionFactory = options.socketConnectionFactory,
		)
	}

	public companion object {
		/**
		 * The minimum server version expected to work. Lower versions may work but are not supported.
		 */
		public val minimumVersion: ServerVersion = ServerVersion(10, 11, 0, 0)

		/**
		 * The exact server version used to generate the API. Should be equal or higher than [minimumVersion].
		 */
		public val apiVersion: ServerVersion = ServerVersion.fromString(ApiConstants.apiVersion)!!
	}
}

public inline fun createKhyloSdk(
	init: KhyloSdkOptions.Builder.() -> Unit,
): KhyloSdk = KhyloSdk(createKhyloSdkOptions(init))
