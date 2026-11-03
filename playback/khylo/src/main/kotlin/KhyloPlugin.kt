package org.khylo.tv.playback.khylo

import androidx.lifecycle.Lifecycle
import org.khylo.tv.playback.core.plugin.playbackPlugin
import org.khylo.tv.playback.khylo.lyrics.LyricsPlayerService
import org.khylo.tv.playback.khylo.mediasegment.MediaSegmentService
import org.khylo.tv.playback.khylo.mediastream.KhyloMediaStreamResolver
import org.khylo.tv.playback.khylo.playsession.PlaySessionService
import org.khylo.tv.playback.khylo.playsession.PlaySessionSocketService
import org.khylo.sdk.api.client.ApiClient
import org.khylo.sdk.model.api.DeviceProfile
import org.khylo.sdk.model.api.MediaSegmentType

fun khyloPlugin(
	api: ApiClient,
	deviceProfileBuilder: () -> DeviceProfile,
	mediaSegmentSkipTypes: Set<MediaSegmentType> = emptySet(),
	lifecycle: Lifecycle? = null,
) = playbackPlugin {
	provide(KhyloMediaStreamResolver(api, deviceProfileBuilder))

	val playSessionService = PlaySessionService(api)
	provide(playSessionService)
	provide(PlaySessionSocketService(api, playSessionService, lifecycle))

	provide(LyricsPlayerService(api))

	if (mediaSegmentSkipTypes.isNotEmpty()) provide(MediaSegmentService(api, mediaSegmentSkipTypes))
}
