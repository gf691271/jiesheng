package com.frank.jiesheng

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem

@OptIn(markerClass = [UnstableApi::class])
object SplitCompositionFactory {
    fun audioClip(uri: Uri, segment: SplitSegment): EditedMediaItem {
        require(segment.startMs >= 0L) { "Segment start must not be negative" }
        require(segment.endMs > segment.startMs) { "Segment end must be after start" }
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(segment.startMs)
            .setEndPositionMs(segment.endMs)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .build()
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()
    }
}
