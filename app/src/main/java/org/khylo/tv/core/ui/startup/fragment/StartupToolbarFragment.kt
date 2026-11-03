package org.khylo.tv.core.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import org.khylo.tv.core.R
import org.khylo.tv.core.ui.shared.toolbar.StartupToolbar

class StartupToolbarFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			StartupToolbar(
				openHelp = {
					parentFragmentManager.commit {
						addToBackStack(null)
						replace<ConnectHelpAlertFragment>(R.id.content_view)
					}
				}
			)
		}
	}
}
