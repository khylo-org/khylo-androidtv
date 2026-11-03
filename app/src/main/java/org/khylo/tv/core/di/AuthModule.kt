package org.khylo.tv.core.di

import org.khylo.tv.core.auth.repository.AuthenticationRepository
import org.khylo.tv.core.auth.repository.AuthenticationRepositoryImpl
import org.khylo.tv.core.auth.repository.ServerRepository
import org.khylo.tv.core.auth.repository.ServerRepositoryImpl
import org.khylo.tv.core.auth.repository.ServerUserRepository
import org.khylo.tv.core.auth.repository.ServerUserRepositoryImpl
import org.khylo.tv.core.auth.repository.SessionRepository
import org.khylo.tv.core.auth.repository.SessionRepositoryImpl
import org.khylo.tv.core.auth.store.AuthenticationPreferences
import org.khylo.tv.core.auth.store.AuthenticationStore
import org.koin.dsl.module

val authModule = module {
	single { AuthenticationStore(get()) }
	single { AuthenticationPreferences(get()) }

	single<AuthenticationRepository> {
		AuthenticationRepositoryImpl(get(), get(), get(), get(), get(), get(defaultDeviceInfo))
	}
	single<ServerRepository> { ServerRepositoryImpl(get(), get()) }
	single<ServerUserRepository> { ServerUserRepositoryImpl(get(), get()) }
	single<SessionRepository> {
		SessionRepositoryImpl(get(), get(), get(), get(), get(defaultDeviceInfo), get(), get(), get())
	}

	factory {
		val serverRepository = get<ServerRepository>()
		serverRepository.currentServer.value?.serverVersion ?: ServerRepository.minimumServerVersion
	}
}
