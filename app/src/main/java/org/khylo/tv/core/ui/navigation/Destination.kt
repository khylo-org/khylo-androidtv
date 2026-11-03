package org.khylo.tv.core.ui.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment
import org.khylo.tv.core.util.createBundle
import kotlin.reflect.KClass

sealed interface Destination {
	data class Fragment(
		val fragment: KClass<out androidx.fragment.app.Fragment>,
		val arguments: Bundle = createBundle(),
	) : Destination
}

inline fun <reified T : Fragment> fragmentDestination(
	noinline arguments: (Bundle.() -> Unit)? = null,
) = Destination.Fragment(
	fragment = T::class,
	arguments = createBundle(arguments),
)
