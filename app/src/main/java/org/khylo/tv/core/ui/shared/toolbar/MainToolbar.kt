package org.khylo.tv.core.ui.shared.toolbar

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.filterNotNull
import org.khylo.tv.core.R
import org.khylo.tv.core.auth.repository.SessionRepository
import org.khylo.tv.core.auth.repository.UserRepository
import org.khylo.tv.core.ui.NowPlayingComposable
import org.khylo.tv.core.ui.base.Icon
import org.khylo.tv.core.ui.base.CoreTheme
import org.khylo.tv.core.ui.base.ProfilePicture
import org.khylo.tv.core.ui.base.ProvideTextStyle
import org.khylo.tv.core.ui.base.Text
import org.khylo.tv.core.ui.base.button.Button
import org.khylo.tv.core.ui.base.button.ButtonDefaults
import org.khylo.tv.core.ui.base.button.IconButton
import org.khylo.tv.core.ui.base.button.IconButtonDefaults
import org.khylo.tv.core.ui.navigation.ActivityDestinations
import org.khylo.tv.core.ui.navigation.Destinations
import org.khylo.tv.core.ui.navigation.NavigationRepository
import org.khylo.tv.core.ui.playback.MediaManager
import org.khylo.tv.core.ui.settings.compat.SettingsViewModel
import org.khylo.tv.core.util.apiclient.getUrl
import org.khylo.tv.core.util.apiclient.primaryImage
import org.khylo.sdk.api.client.ApiClient
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

enum class MainToolbarActiveButton {
	User,
	Home,
	Search,

	None,
}

@Composable
fun MainToolbar(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
) {
	val userRepository = koinInject<UserRepository>()
	val api = koinInject<ApiClient>()

	// Prevent user image to disappear when signing out by skipping null values
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }

	MainToolbar(
		userImage = userImage,
		activeButton = activeButton,
	)
}

@Composable
private fun MainToolbar(
	userImage: String? = null,
	activeButton: MainToolbarActiveButton,
) {
	val focusRequester = remember { FocusRequester() }
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val activity = LocalActivity.current
	val activeButtonColors = ButtonDefaults.colors(
		containerColor = CoreTheme.colorScheme.buttonActive,
		contentColor = CoreTheme.colorScheme.onButtonActive,
	)

	Toolbar(
		modifier = Modifier
			.focusRestorer(focusRequester)
			.focusGroup(),
		start = {
			ToolbarButtons {
				val userImagePainter = rememberAsyncImagePainter(userImage)
				val userImageState by userImagePainter.state.collectAsState()
				val userImageVisible = userImageState is AsyncImagePainter.State.Success

				IconButton(
					onClick = {
						if (activeButton != MainToolbarActiveButton.User) {
							mediaManager.clearAudioQueue()
							sessionRepository.destroyCurrentSession()

							// Open login activity
							activity?.startActivity(ActivityDestinations.startup(activity))
							activity?.finishAfterTransition()
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.User) activeButtonColors else ButtonDefaults.colors(),
					contentPadding = if (userImageVisible) PaddingValues(3.dp) else IconButtonDefaults.ContentPadding,
				) {
					ProfilePicture(
						url = userImage,
						contentDescription = stringResource(R.string.lbl_switch_user),
						modifier = Modifier
							.aspectRatio(1f)
							.clip(IconButtonDefaults.Shape)
					)
				}

				NowPlayingComposable(
					onFocusableChange = {},
				)
			}
		},
		center = {
			ToolbarButtons(
				modifier = Modifier
					.focusRequester(focusRequester)
			) {
				ProvideTextStyle(CoreTheme.typography.default.copy(fontWeight = FontWeight.Bold)) {
					Button(
						onClick = {
							if (activeButton != MainToolbarActiveButton.Home) {
								navigationRepository.navigate(
									Destinations.home,
									replace = true,
								)
							}
						},
						colors = if (activeButton == MainToolbarActiveButton.Home) activeButtonColors else ButtonDefaults.colors(),
						content = { Text(stringResource(R.string.lbl_home)) }
					)
					Button(
						onClick = {
							if (activeButton != MainToolbarActiveButton.Search) {
								navigationRepository.navigate(Destinations.search())
							}
						},
						colors = if (activeButton == MainToolbarActiveButton.Search) activeButtonColors else ButtonDefaults.colors(),
						content = { Text(stringResource(R.string.lbl_search)) }
					)
				}
			}
		},
		end = {
			ToolbarButtons {
				IconButton(
					onClick = { settingsViewModel.show() },
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
						contentDescription = stringResource(R.string.lbl_settings),
					)
				}

				ToolbarClock()
			}
		}
	)
}
