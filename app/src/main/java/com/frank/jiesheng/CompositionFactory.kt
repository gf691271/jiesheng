package com.frank.jiesheng

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence

@OptIn(markerClass = [UnstableApi::class])
object CompositionFactory {
    fun audioOnly(inputs: List<Uri>): Composition {
        require(inputs.size >= 2) { "At least two audio inputs are required" }
        val items = inputs.map { uri ->
            EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
        }
        val sequence = EditedMediaItemSequence.withAudioFrom(items)
        return Composition.Builder(sequence)
            .setTransmuxAudio(false)
            .build()
    }
}
