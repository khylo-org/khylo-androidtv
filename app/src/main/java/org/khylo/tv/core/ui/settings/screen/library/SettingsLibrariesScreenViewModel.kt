package org.khylo.tv.core.ui.settings.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.khylo.tv.core.data.repository.UserViewsRepository
import org.khylo.sdk.model.api.CollectionType

class SettingsLibrariesScreenViewModel(
	private val userViewsRepository: UserViewsRepository,
) : ViewModel() {
	val userViews = userViewsRepository.views
		.map { it.toList() }
		.stateIn(
			viewModelScope,
			SharingStarted.WhileSubscribed(5_000),
			emptyList()
		)

	fun allowGridView(collectionType: CollectionType?) = userViewsRepository.allowGridView(collectionType)
}
