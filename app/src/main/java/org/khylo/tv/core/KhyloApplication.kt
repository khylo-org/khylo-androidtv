package org.khylo.tv.core

import android.app.Application
import android.content.Context
import org.khylo.tv.core.telemetry.TelemetryService

class KhyloApplication : Application() {
	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base)
		TelemetryService.init(this)
	}
}
