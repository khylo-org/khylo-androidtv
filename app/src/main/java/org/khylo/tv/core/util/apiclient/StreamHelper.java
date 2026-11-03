package org.khylo.tv.core.util.apiclient;

import org.khylo.sdk.model.api.MediaSourceInfo;
import org.khylo.sdk.model.api.MediaStream;
import org.khylo.sdk.model.api.MediaStreamType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StreamHelper {
    public static List<MediaStream> getSubtitleStreams(MediaSourceInfo mediaSource) {
        return getStreams(mediaSource, MediaStreamType.SUBTITLE);
    }

    public static List<MediaStream> getAudioStreams(MediaSourceInfo mediaSource) {
        return getStreams(mediaSource, MediaStreamType.AUDIO);
    }

    private static List<MediaStream> getStreams(MediaSourceInfo mediaSource, MediaStreamType type) {
        if (mediaSource == null) return Collections.emptyList();

        List<MediaStream> streams = mediaSource.getMediaStreams();
        ArrayList<MediaStream> ret = new ArrayList<>();
        if (streams != null) {
            for (MediaStream stream : streams) {
                if (stream.getType() == type) {
                    ret.add(stream);
                }
            }
        }

        return ret;
    }
}
