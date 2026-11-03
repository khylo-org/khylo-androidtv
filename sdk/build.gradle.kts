plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.serialization)
}

android {
	namespace = "org.khylo.sdk"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()

		// Keeps the server's data types and errors intact when the app is shrunk
		consumerProguardFiles("consumer-rules.pro")
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
	}
}

dependencies {
	// Kotlin
	api(libs.kotlinx.coroutines)
	api(libs.kotlinx.serialization.json)

	// Network and logging
	implementation(libs.okhttp)
	implementation(libs.kotlin.logging)

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)
}
