plugins {
	alias(libs.plugins.android.library)
}

android {
	namespace = "org.khylo.tv.designsystem"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
	}
}

dependencies {
	implementation(libs.androidx.compose.ui.graphics)
}
