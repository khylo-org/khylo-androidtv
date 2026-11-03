package org.khylo.tv.core.util.sdk

import org.khylo.sdk.model.api.MediaSegmentDto
import org.khylo.sdk.model.extensions.ticks
import kotlin.time.Duration

val MediaSegmentDto.start get() = startTicks.ticks
val MediaSegmentDto.end get() = endTicks.ticks

val MediaSegmentDto.duration get() = (endTicks - startTicks).ticks.coerceAtLeast(Duration.ZERO)
