package com.fixlens.tracking

import com.fixlens.vision.PxBox

/** Where the markers are, in analysis space (see docs/marker-tracking.md §1). Published by [FlowTracker] every frame. */
data class MarkerState(
    /** Changes on every new seed (a new answer or a re-ground), so the UI can replay its lock-on animation. */
    val seedId: Int,
    /** Every part pointed at (one box, or e.g. every screw of a cover), in the order the VLM gave them. */
    val targets: List<Target>,
    val frameWidth: Int,
    val frameHeight: Int,
    val confidence: Float,
    val status: Status,
    val timestampNs: Long,
    /** This seed is a silent re-ground of parts already shown (see [FlowTracker.Seed.quiet]). */
    val quiet: Boolean = false,
) {
    data class Target(val box: PxBox, val label: String, val isPoint: Boolean)

    enum class Status {
        /** Locked on and following the part. */
        Tracking,
        /** Tracking just failed: the marker stays where it was for a moment. */
        Holding,
        /** Gone for longer than the hold time: the UI fades the marker out. */
        Lost,
    }
}
