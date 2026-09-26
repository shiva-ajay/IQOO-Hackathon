package com.fixlens.tracking

import com.fixlens.vision.PxBox

/** Where the marker is, in analysis space (see docs/marker-tracking.md §1). Published by [FlowTracker] every frame. */
data class MarkerState(
    /** Changes on every new seed (a new answer or a re-ground), so the UI can replay its lock-on animation. */
    val seedId: Int,
    val box: PxBox,
    val frameWidth: Int,
    val frameHeight: Int,
    val label: String,
    val confidence: Float,
    val status: Status,
    val timestampNs: Long,
) {
    enum class Status {
        /** Locked on and following the part. */
        Tracking,
        /** Tracking just failed: the marker stays where it was for a moment. */
        Holding,
        /** Gone for longer than the hold time: the UI fades the marker out. */
        Lost,
    }
}
