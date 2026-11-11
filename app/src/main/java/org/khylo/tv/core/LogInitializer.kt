package org.khylo.tv.core

import android.content.Context
import androidx.startup.Initializer
import timber.log.Timber

class LogInitializer : Initializer<Unit> {
	override fun create(context: Context) {
		// Enable improved logging for leaking resources
		// https://wh0.github.io/2020/08/12/closeguard.html
		if (BuildConfig.DEBUG) {
			try {
				Class.forName("dalvik.system.CloseGuard")
					.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
					.invoke(null, true)
			} catch (e: ReflectiveOperationException) {
				@Suppress("TooGenericExceptionThrown")
				throw RuntimeException(e)
			}
		}

		// Initialize the logging library. A release build writes only warnings and errors to the system log (anyone with adb
		// can read it); the full log is for debug builds, or for a developer who turns it on with adb (see ReleaseTree).
		if (BuildConfig.DEBUG) {
			Timber.plant(Timber.DebugTree())
			Timber.i("Debug tree planted")
		} else {
			Timber.plant(ReleaseTree())
		}
	}

	override fun dependencies() = emptyList<Class<out Initializer<*>>>()
}
