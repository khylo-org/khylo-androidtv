package org.khylo.tv.core.data.eventhandling

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.khylo.tv.core.data.model.DataRefreshService
import org.khylo.tv.core.ui.itemhandling.ItemLauncher
import org.khylo.tv.core.ui.navigation.Destinations
import org.khylo.tv.core.ui.navigation.NavigationRepository
import org.khylo.tv.core.ui.playback.MediaManager
import org.khylo.tv.core.ui.playback.PlaybackControllerContainer
import org.khylo.tv.core.ui.playback.setSubtitleIndex
import org.khylo.tv.core.util.PlaybackHelper
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.api.client.exception.ApiClientException
import org.khylo.sdk.api.client.extensions.sessionApi
import org.khylo.sdk.api.client.extensions.userLibraryApi
import org.khylo.sdk.api.sockets.subscribe
import org.khylo.sdk.api.sockets.subscribeGeneralCommand
import org.khylo.sdk.api.sockets.subscribeGeneralCommands
import org.khylo.sdk.model.api.BaseItemKind
import org.khylo.sdk.model.api.GeneralCommandType
import org.khylo.sdk.model.api.LibraryChangedMessage
import org.khylo.sdk.model.api.LibraryUpdateInfo
import org.khylo.sdk.model.api.MediaType
import org.khylo.sdk.model.api.PlayMessage
import org.khylo.sdk.model.api.PlaystateCommand
import org.khylo.sdk.model.api.PlaystateMessage
import org.khylo.sdk.model.extensions.get
import org.khylo.sdk.model.extensions.getValue
import org.khylo.sdk.model.extensions.ticks
import org.khylo.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.Instant
import java.util.UUID

class SocketHandler(
	private val context: Context,
	private val api: ApiClient,
	private val dataRefreshService: DataRefreshService,
	private val mediaManager: MediaManager,
	private val playbackControllerContainer: PlaybackControllerContainer,
	private val navigationRepository: NavigationRepository,
	private val audioManager: AudioManager,
	private val itemLauncher: ItemLauncher,
	private val playbackHelper: PlaybackHelper,
	private val lifecycle: Lifecycle,
) {
	init {
		lifecycle.coroutineScope.launch(Dispatchers.IO) {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				subscribe(this)
			}
		}
	}

	suspend fun updateSession() {
		try {
			withContext(Dispatchers.IO) {
				api.sessionApi.postCapabilities(
					playableMediaTypes = listOf(MediaType.VIDEO, MediaType.AUDIO),
					supportsMediaControl = true,
					supportedCommands = buildList {
						add(GeneralCommandType.DISPLAY_CONTENT)
						add(GeneralCommandType.SET_SUBTITLE_STREAM_INDEX)
						add(GeneralCommandType.SET_AUDIO_STREAM_INDEX)

						add(GeneralCommandType.DISPLAY_MESSAGE)
						add(GeneralCommandType.SEND_STRING)

						// Note: These are used in the PlaySessionSocketService
						if (!audioManager.isVolumeFixed) {
							add(GeneralCommandType.VOLUME_UP)
							add(GeneralCommandType.VOLUME_DOWN)
							add(GeneralCommandType.SET_VOLUME)

							add(GeneralCommandType.MUTE)
							add(GeneralCommandType.UNMUTE)
							add(GeneralCommandType.TOGGLE_MUTE)
						}
					},
				)
			}
		} catch (err: ApiClientException) {
			Timber.e(err, "Unable to update capabilities")
		}
	}

	private fun subscribe(coroutineScope: CoroutineScope) = api.webSocket.apply {
		// Library
		subscribe<LibraryChangedMessage>()
			.onEach { message -> message.data?.let(::onLibraryChanged) }
			.launchIn(coroutineScope)

		// Media playback
		subscribe<PlayMessage>()
			.onEach { message -> onPlayMessage(message) }
			.launchIn(coroutineScope)

		subscribe<PlaystateMessage>()
			.onEach { message -> onPlayStateMessage(message) }
			.launchIn(coroutineScope)

		subscribeGeneralCommand(GeneralCommandType.SET_SUBTITLE_STREAM_INDEX)
			.onEach { message ->
				val index = message["index"]?.toIntOrNull() ?: return@onEach

				withContext(Dispatchers.Main) {
					playbackControllerContainer.playbackController?.setSubtitleIndex(index)
				}
			}
			.launchIn(coroutineScope)

		subscribeGeneralCommand(GeneralCommandType.SET_AUDIO_STREAM_INDEX)
			.onEach { message ->
				val index = message["index"]?.toIntOrNull() ?: return@onEach

				withContext(Dispatchers.Main) {
					playbackControllerContainer.playbackController?.switchAudioStream(index)
				}
			}
			.launchIn(coroutineScope)

		// General commands
		subscribeGeneralCommand(GeneralCommandType.DISPLAY_CONTENT)
			.onEach { message ->
				val itemId by message
				val itemType by message

				val itemUuid = itemId?.toUUIDOrNull()
				val itemKind = itemType?.let { type ->
					BaseItemKind.entries.find { value ->
						value.serialName.equals(type, true)
					}
				}

				if (itemUuid != null && itemKind != null) onDisplayContent(itemUuid, itemKind)
			}
			.launchIn(coroutineScope)

		subscribeGeneralCommands(setOf(GeneralCommandType.DISPLAY_MESSAGE, GeneralCommandType.SEND_STRING))
			.onEach { message ->
				val header by message
				val text by message
				val string by message

				// A mirror following a Khylo TV channel sends the channel as a string: tune to it instead of showing it
				val channel = string?.takeIf { it.startsWith("khylo-tv-channel:") }
				if (channel != null) {
					org.khylo.tv.handoff.HandoffInbox.tuneChannel(channel.removePrefix("khylo-tv-channel:"))
					return@onEach
				}

				onDisplayMessage(header, text ?: string)
			}
			.launchIn(coroutineScope)
	}

	private fun onLibraryChanged(info: LibraryUpdateInfo) {
		Timber.d(buildString {
			appendLine("Library changed.")
			appendLine("Added ${info.itemsAdded.size} items")
			appendLine("Removed ${info.itemsRemoved.size} items")
			appendLine("Updated ${info.itemsUpdated.size} items")
		})

		if (info.itemsAdded.any() || info.itemsRemoved.any())
			dataRefreshService.lastLibraryChange = Instant.now()
	}

	/** Another of the viewer's devices wants this TV to play something (a handoff, or a "play on" request). */
	private fun onPlayMessage(message: PlayMessage) {
		val data = message.data ?: return
		val itemId = data.itemIds?.firstOrNull() ?: return
		org.khylo.tv.handoff.HandoffInbox.play(
			org.khylo.tv.handoff.IncomingPlay(
				itemId = itemId,
				startPositionMs = data.startPositionTicks?.let { it / 10_000 },
				mediaSourceId = data.mediaSourceId,
				audioStreamIndex = data.audioStreamIndex,
				subtitleStreamIndex = data.subtitleStreamIndex,
			)
		)
	}

	@Suppress("ComplexMethod")
	private suspend fun onPlayStateMessage(message: PlaystateMessage) = withContext(Dispatchers.Main) {
		Timber.i("Received PlayStateMessage with command ${message.data?.command}")

		// Khylo's own player closes when another device takes over (handoff)
		if (message.data?.command == PlaystateCommand.STOP) org.khylo.tv.handoff.HandoffInbox.stop()

		// Pause, resume and seek reach Khylo's own player the same way (a mirror following along)
		when (message.data?.command) {
			PlaystateCommand.PAUSE -> org.khylo.tv.handoff.HandoffInbox.command(org.khylo.tv.handoff.RemoteCommand.Pause)
			PlaystateCommand.UNPAUSE -> org.khylo.tv.handoff.HandoffInbox.command(org.khylo.tv.handoff.RemoteCommand.Unpause)
			PlaystateCommand.SEEK -> message.data?.seekPositionTicks?.let {
				org.khylo.tv.handoff.HandoffInbox.command(org.khylo.tv.handoff.RemoteCommand.SeekTo(it / 10_000))
			}

			else -> Unit
		}

		// Audio playback uses (Rewrite)MediaManager, (legacy) video playback uses playbackController
		when {
			mediaManager.hasAudioQueueItems() -> {
				Timber.i("Ignoring PlayStateMessage: should be handled by PlaySessionSocketService")
				return@withContext
			}

			// PlaybackController
			else -> {
				val playbackController = playbackControllerContainer.playbackController
				when (message.data?.command) {
					PlaystateCommand.STOP -> playbackController?.endPlayback(true)
					PlaystateCommand.PAUSE, PlaystateCommand.UNPAUSE, PlaystateCommand.PLAY_PAUSE -> playbackController?.playPause()
					PlaystateCommand.NEXT_TRACK -> playbackController?.next()
					PlaystateCommand.PREVIOUS_TRACK -> playbackController?.prev()
					PlaystateCommand.SEEK -> playbackController?.seek(
						message.data?.seekPositionTicks?.ticks?.inWholeMilliseconds ?: 0
					)

					PlaystateCommand.REWIND -> playbackController?.rewind()
					PlaystateCommand.FAST_FORWARD -> playbackController?.fastForward()

					null -> Unit
				}
			}
		}
	}

	private suspend fun onDisplayContent(itemId: UUID, itemKind: BaseItemKind) = withContext(Dispatchers.Main) {
		val playbackController = playbackControllerContainer.playbackController

		if (playbackController?.isPlaying == true || playbackController?.isPaused == true) {
			Timber.i("Not launching $itemId: playback in progress")
			return@withContext
		}

		Timber.i("Launching $itemId")

		when (itemKind) {
			BaseItemKind.USER_VIEW,
			BaseItemKind.COLLECTION_FOLDER -> {
				val item = withContext(Dispatchers.IO) { api.userLibraryApi.getItem(itemId = itemId).content }
				itemLauncher.launchUserView(item)
			}

			else -> navigationRepository.navigate(Destinations.itemDetails(itemId))
		}
	}

	private fun onDisplayMessage(header: String?, text: String?) {
		val toastMessage = buildString {
			if (!header.isNullOrBlank()) append(header, ": ")
			append(text)
		}

		runBlocking(Dispatchers.Main) {
			Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
		}
	}
}
