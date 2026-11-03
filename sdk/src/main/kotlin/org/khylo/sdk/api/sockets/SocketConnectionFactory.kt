package org.khylo.sdk.api.sockets

import kotlinx.coroutines.CoroutineScope
import org.khylo.sdk.api.client.HttpClientOptions

/**
 * The factory to create a [SocketConnection].
 */
public fun interface SocketConnectionFactory {
	public fun create(clientOptions: HttpClientOptions, scope: CoroutineScope): SocketConnection
}
