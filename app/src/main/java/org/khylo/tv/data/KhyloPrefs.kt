package org.khylo.tv.data

import android.content.Context

/** Settings that belong to this TV rather than the profile (it may sit on a slower connection). */
class KhyloPrefs(context: Context) {
	private val prefs = context.getSharedPreferences("khylo_tv", Context.MODE_PRIVATE)

	/** Highest bitrate to stream at, in bits per second; null lets the server decide. */
	var maxBitrate: Int?
		get() = prefs.getInt(KEY_MAX_BITRATE, 0).takeIf { it > 0 }
		set(value) = prefs.edit().putInt(KEY_MAX_BITRATE, value ?: 0).apply()

	/** Jump over intros (and recaps) without asking. */
	var autoSkipIntro: Boolean
		get() = prefs.getBoolean(KEY_SKIP_INTRO, false)
		set(value) = prefs.edit().putBoolean(KEY_SKIP_INTRO, value).apply()

	/** At the end credits, go straight to the next episode (or finish a movie). */
	var autoSkipCredits: Boolean
		get() = prefs.getBoolean(KEY_SKIP_CREDITS, false)
		set(value) = prefs.edit().putBoolean(KEY_SKIP_CREDITS, value).apply()

	companion object {
		private const val KEY_MAX_BITRATE = "max_bitrate"
		private const val KEY_SKIP_INTRO = "auto_skip_intro"
		private const val KEY_SKIP_CREDITS = "auto_skip_credits"

		/** Streaming quality choices: label to bits per second (null = automatic). */
		val qualityOptions: List<Pair<String, Int?>> = listOf(
			"Automatic" to null,
			"Up to 120 Mbps" to 120_000_000,
			"Up to 60 Mbps" to 60_000_000,
			"Up to 20 Mbps" to 20_000_000,
			"Up to 10 Mbps" to 10_000_000,
			"Up to 4 Mbps" to 4_000_000,
			"Up to 1.5 Mbps (data saver)" to 1_500_000,
		)
	}
}
