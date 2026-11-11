package org.khylo.tv.core.di

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.NetworkFetcher
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import coil3.svg.SvgDecoder
import coil3.util.Logger
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.khylo.tv.core.BuildConfig
import org.khylo.tv.core.auth.repository.ServerRepository
import org.khylo.tv.core.auth.repository.UserRepository
import org.khylo.tv.core.auth.repository.UserRepositoryImpl
import org.khylo.tv.core.data.eventhandling.SocketHandler
import org.khylo.tv.core.data.model.DataRefreshService
import org.khylo.tv.core.data.repository.CustomMessageRepository
import org.khylo.tv.core.data.repository.CustomMessageRepositoryImpl
import org.khylo.tv.core.data.repository.ExternalAppRepository
import org.khylo.tv.core.data.repository.ItemMutationRepository
import org.khylo.tv.core.data.repository.ItemMutationRepositoryImpl
import org.khylo.tv.core.data.repository.NotificationsRepository
import org.khylo.tv.core.data.repository.NotificationsRepositoryImpl
import org.khylo.tv.core.data.repository.UserViewsRepository
import org.khylo.tv.core.data.repository.UserViewsRepositoryImpl
import org.khylo.tv.core.data.service.BackgroundService
import org.khylo.tv.core.integration.dream.DreamViewModel
import org.khylo.tv.core.ui.InteractionTrackerViewModel
import org.khylo.tv.core.ui.itemhandling.ItemLauncher
import org.khylo.tv.core.ui.navigation.Destinations
import org.khylo.tv.core.ui.navigation.NavigationRepository
import org.khylo.tv.core.ui.navigation.NavigationRepositoryImpl
import org.khylo.tv.core.ui.playback.PlaybackControllerContainer
import org.khylo.tv.core.ui.playback.external.DefaultExternalPlayerApi
import org.khylo.tv.core.ui.playback.external.ExternalPlayerApi
import org.khylo.tv.core.ui.playback.external.MpvExternalPlayerApi
import org.khylo.tv.core.ui.playback.external.MxExternalPlayerApi
import org.khylo.tv.core.ui.playback.external.VimuExternalPlayerApi
import org.khylo.tv.core.ui.playback.external.VlcExternalPlayerApi
import org.khylo.tv.core.ui.playback.nextup.NextUpViewModel
import org.khylo.tv.core.ui.playback.segment.MediaSegmentRepository
import org.khylo.tv.core.ui.playback.segment.MediaSegmentRepositoryImpl
import org.khylo.tv.core.ui.playback.stillwatching.StillWatchingViewModel
import org.khylo.tv.core.ui.player.photo.PhotoPlayerViewModel
import org.khylo.tv.core.ui.search.SearchFragmentDelegate
import org.khylo.tv.core.ui.search.SearchRepository
import org.khylo.tv.core.ui.search.SearchRepositoryImpl
import org.khylo.tv.core.ui.search.SearchViewModel
import org.khylo.tv.core.ui.settings.compat.SettingsViewModel
import org.khylo.tv.core.ui.settings.screen.library.SettingsLibrariesScreenViewModel
import org.khylo.tv.core.ui.startup.ServerAddViewModel
import org.khylo.tv.core.ui.startup.StartupViewModel
import org.khylo.tv.core.ui.startup.UserLoginViewModel
import org.khylo.tv.core.util.AndroidVersion
import org.khylo.tv.core.util.KeyProcessor
import org.khylo.tv.core.util.MarkdownRenderer
import org.khylo.tv.core.util.PlaybackHelper
import org.khylo.tv.core.util.apiclient.ReportingHelper
import org.khylo.tv.core.util.coil.CoilTimberLogger
import org.khylo.tv.core.util.coil.createCoilConnectivityChecker
import org.khylo.tv.core.util.sdk.SdkPlaybackHelper
import org.khylo.sdk.android.androidDevice
import org.khylo.sdk.api.client.HttpClientOptions
import org.khylo.sdk.api.okhttp.OkHttpFactory
import org.khylo.sdk.createKhyloSdk
import org.khylo.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.khylo.sdk.KhyloSdk
import org.khylo.tv.data.trustingChosenCertificates

val defaultDeviceInfo = named("defaultDeviceInfo")

val appModule = module {
	// SDK
	single(defaultDeviceInfo) { androidDevice(get()) }
	// The certificates the owner chose to trust (self-signed or home-made ones); every connection follows them
	single { org.khylo.tv.data.TrustedCertificates(org.khylo.tv.data.SharedPreferencesPinStore(androidContext())) }
	// Remembers what server names resolve to, so a home-only name keeps working when this box can't look it up
	single {
		// Asked for when a request is made (not now), because the API client itself is made from this factory
		val scope = this
		val api by lazy { scope.get<org.khylo.sdk.api.client.ApiClient>() }
		OkHttpFactory(
			okhttp3.OkHttpClient.Builder()
				.trustingChosenCertificates(get())
				.dns(org.khylo.tv.data.RememberingDns(androidContext()))
				// Requests to the server this TV is signed in to are signed in with a header, so no key goes in an address
				.addInterceptor(
					org.khylo.tv.data.ServerAuthInterceptor {
						val base = api.baseUrl?.toHttpUrlOrNull()
						val token = api.accessToken
						if (base == null || token == null) null else org.khylo.tv.data.ServerSignIn(
							base,
							org.khylo.sdk.api.client.util.AuthorizationHeaderBuilder.buildHeader(
								api.clientInfo.name,
								api.clientInfo.version,
								api.deviceInfo.id,
								api.deviceInfo.name,
								token,
							)
						)
					}
				)
				// Plain http is for the home network only; anywhere else needs https
				.addNetworkInterceptor(org.khylo.tv.data.CleartextGuard())
				// The server sees (and logs) this header: say who we are, nothing else
				.addNetworkInterceptor { chain ->
					chain.proceed(chain.request().newBuilder().header("User-Agent", "Khylo TV/${BuildConfig.VERSION_NAME}").build())
				}
				.build()
		)
	}
	single { HttpClientOptions() }
	single {
		createKhyloSdk {
			context = androidContext()

			// Add client info
			val clientName = buildString {
				append("Khylo TV")
				if (BuildConfig.DEBUG) append(" (debug)")
			}
			clientInfo = ClientInfo(clientName, BuildConfig.VERSION_NAME)
			deviceInfo = get(defaultDeviceInfo)

			// Change server version
			minimumServerVersion = ServerRepository.minimumServerVersion

			// Use our own shared factory instance
			apiClientFactory = get<OkHttpFactory>()
			socketConnectionFactory = get<OkHttpFactory>()
		}
	}

	single {
		// Create an empty API instance, the actual values are set by the SessionRepository
		get<KhyloSdk>().createApi(httpClientOptions = get<HttpClientOptions>())
	}

	single { SocketHandler(get(), get(), get(), get(), get(), get(), get(), get(), get(), ProcessLifecycleOwner.get().lifecycle) }

	// Coil (images)
	single {
		val okHttpFactory = get<OkHttpFactory>()
		val httpClientOptions = get<HttpClientOptions>()

		@OptIn(ExperimentalCoilApi::class)
		OkHttpNetworkFetcherFactory(
			callFactory = { okHttpFactory.createClient(httpClientOptions) },
			connectivityChecker = ::createCoilConnectivityChecker,
		)
	}

	single {
		ImageLoader.Builder(androidContext()).apply {
			serviceLoaderEnabled(false)
			logger(CoilTimberLogger(if (BuildConfig.DEBUG) Logger.Level.Warn else Logger.Level.Error))

			components {
				add(get<NetworkFetcher.Factory>())

				if (AndroidVersion.isAtLeastP) add(AnimatedImageDecoder.Factory())
				else add(GifDecoder.Factory())
				add(SvgDecoder.Factory())
			}
		}.build()
	}

	// Non API related
	single { DataRefreshService() }
	single { PlaybackControllerContainer() }
	single { InteractionTrackerViewModel(get(), get()) }

	single<UserRepository> { UserRepositoryImpl() }
	single<UserViewsRepository> { UserViewsRepositoryImpl(get()) }
	single<NotificationsRepository> { NotificationsRepositoryImpl(get(), get()) }
	single<ItemMutationRepository> { ItemMutationRepositoryImpl(get(), get()) }
	single<CustomMessageRepository> { CustomMessageRepositoryImpl() }
	single<NavigationRepository> { NavigationRepositoryImpl(Destinations.home) }
	single<SearchRepository> { SearchRepositoryImpl(get()) }
	single<MediaSegmentRepository> { MediaSegmentRepositoryImpl(get(), get()) }
	single<ExternalAppRepository> { ExternalAppRepository(get(), getAll(), get<DefaultExternalPlayerApi>()) }

	// External player APIs
	single { VlcExternalPlayerApi() } bind ExternalPlayerApi::class
	single { MxExternalPlayerApi() } bind ExternalPlayerApi::class
	single { MpvExternalPlayerApi() } bind ExternalPlayerApi::class
	single { VimuExternalPlayerApi() } bind ExternalPlayerApi::class
	single { DefaultExternalPlayerApi() }

	viewModel { StartupViewModel(get(), get(), get(), get()) }
	viewModel { UserLoginViewModel(get(), get(), get(), get(defaultDeviceInfo)) }
	viewModel { ServerAddViewModel(get()) }
	viewModel { NextUpViewModel(get(), get(), get()) }
	viewModel { StillWatchingViewModel(get(), get(), get(), get()) }
	viewModel { PhotoPlayerViewModel(get(), get()) }
	viewModel { SearchViewModel(get()) }
	viewModel { DreamViewModel(get(), get(), get(), get(), get()) }
	viewModel { SettingsViewModel() }
	viewModel { SettingsLibrariesScreenViewModel(get()) }

	single { BackgroundService(get(), get(), get(), get(), get()) }

	single { MarkdownRenderer(get()) }
	single { ItemLauncher() }
	single { KeyProcessor() }
	single { ReportingHelper(get(), get()) }
	single<PlaybackHelper> { SdkPlaybackHelper(get(), get(), get(), get()) }

	factory { (context: Context) -> SearchFragmentDelegate(context, get(), get()) }
}
