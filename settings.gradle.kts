enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "khylo-tv"

// Application
include(":app")

// Modules
include(":design")
include(":playback:core")
include(":playback:khylo")
include(":playback:media3:exoplayer")
include(":playback:media3:session")
include(":preference")
include(":sdk")

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		google()
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
		google()

		// The video decoder is kept in this repository
		maven(uri("third_party/maven")) {
			content { includeGroup("org.khylo.media3") }
		}
	}
}
