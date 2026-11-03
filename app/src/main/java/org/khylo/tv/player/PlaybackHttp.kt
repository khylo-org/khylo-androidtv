package org.khylo.tv.player

import okhttp3.OkHttpClient
import java.time.Duration

/**
 * The app's server connection gives up on any request after 30 seconds, which is right for loading
 * pages but cuts a stream off every 30 seconds mid-episode (heard as dropouts, most of all through a
 * soundbar). Players use the same connection without that limit.
 */
internal fun OkHttpClient.withoutCallTimeout(): OkHttpClient = newBuilder().callTimeout(Duration.ZERO).build()
