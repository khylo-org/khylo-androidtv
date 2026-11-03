package org.khylo.sdk.api.sockets.exception

import org.khylo.sdk.api.client.exception.ApiClientException

/**
 * Base exception for the SocketApi and related classes.
 */
public open class SocketException(
	message: String? = null,
	cause: Throwable? = null,
) : ApiClientException(message, cause)
