package org.khylo.tv.core.di

import org.khylo.tv.core.util.ImageHelper
import org.koin.dsl.module

val utilsModule = module {
	single { ImageHelper(get()) }
}
