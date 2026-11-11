package org.khylo.tv.core

import android.util.Log
import timber.log.Timber

/** When a release build writes a log line to the system log. */
object LogPolicy {
	/** A developer turns the full log on with: adb shell setprop log.tag.KhyloDebug DEBUG */
	const val DEBUG_SWITCH_TAG = "KhyloDebug"

	fun shouldLog(priority: Int, debugSwitchOn: Boolean): Boolean = priority >= Log.WARN || debugSwitchOn
}

/**
 * The log of a release build: warnings and errors only, unless the developer's switch is on. The system log can be read
 * by anyone who connects to the TV with adb, and the full log has addresses and searches in it.
 */
class ReleaseTree : Timber.Tree() {
	override fun isLoggable(tag: String?, priority: Int): Boolean =
		LogPolicy.shouldLog(priority, Log.isLoggable(LogPolicy.DEBUG_SWITCH_TAG, Log.DEBUG))

	override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
		Log.println(priority, tag ?: "Khylo", if (t == null) message else "$message\n${Log.getStackTraceString(t)}")
	}
}
