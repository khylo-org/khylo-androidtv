package org.khylo.tv.core.data.compat

import org.khylo.sdk.model.api.PlaybackErrorCode

class PlaybackException : RuntimeException() {
	var errorCode = PlaybackErrorCode.NOT_ALLOWED
}
