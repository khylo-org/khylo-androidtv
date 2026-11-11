package org.khylo.tv.core.ui.navigation

import android.content.Context
import android.content.Intent
import org.khylo.tv.core.ui.startup.StartupActivity

object ActivityDestinations {
	fun startup(context: Context, hideSplash: Boolean = true) = Intent(context, StartupActivity::class.java).apply {
		putExtra(StartupActivity.EXTRA_HIDE_SPLASH, hideSplash)
		// Remove history to prevent user going back to current activity
		addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
	}
}
