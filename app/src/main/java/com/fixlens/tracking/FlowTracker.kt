package com.fixlens.tracking

import android.util.Log
import com.fixlens.app.TAG
import com.fixlens.camera.FrameRingBuffer
import com.fixlens.tracking.MarkerState.Status
import com.fixlens.vision.PxBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps the markers on their parts between VLM answers ("fast eyes", CLAUDE.md §10). Runs on the analysis
 * thread, in analysis space. One answer can point at several parts (every screw of a cover); they're tracked
 * as a group: one set of corners over the area they span, one motion model, applied to every target.
 *
 * - **Seed:** corners (`goodFeaturesToTrack`) inside the targets' area on the keyframe's buffered frame. A
 *   patch with few corners (a small dipstick handle) is widened and tracked as a whole, and each box keeps
 *   its place inside it. Targets that stream in later ([add]) join the group with corners of their own.
 * - **Fast-forward:** LK optical flow from the keyframe through the buffered frames up to "now", so the
 *   VLM's 2–4 s delay doesn't leave the marker behind.
 * - **Per frame:** forward-backward LK, then a RANSAC homography from each point's seed position to its
 *   current one (a similarity transform or median shift when that is unstable). The seed box goes through
 *   that model, and the centre and size are EMA-smoothed.
 * - **Lost:** the marker holds for [HOLD_NS], then fades. The last good frame stays the LK anchor, so a
 *   brief occlusion recovers by itself. Low confidence for over [REGROUND_AFTER_NS] asks [onReGround] for
 *   a fresh VLM box.
 *
 * [seed] and [clear] may be called from any thread; they take effect on the next frame.
 */
class FlowTracker(private val onReGround: (labels: List<String>) -> Unit) {

    /** One part to point at: [box] in analysis space. A point target is a small box, drawn as a ring. */
    data class Target(val box: PxBox, val label: String, val isPoint: Boolean = false)

    /** Targets to follow, all on the frame stamped [keyframeTimestampNs]. */
    class Seed(val targets: List<Target>, val keyframeTimestampNs: Long, val isCurrent: () -> Boolean)

    private sealed interface Command {
        class Start(val seed: Seed) : Command
        class Add(val seed: Seed) : Command
        data object Stop : Command
    }

    /** Applied in order on the next frame, so a late seed can never jump ahead of a clear() issued before it. */
    private val commands = ConcurrentLinkedQueue<Command>()
    private val epoch = AtomicInteger(0)
    private val _state = MutableStateFlow<MarkerState?>(null)
    val state: StateFlow<MarkerState?> = _state

    /** Replaces whatever is tracked with [seed]'s targets. */
    fun seed(seed: Seed) {
        commands.add(Command.Start(seed))
    }

    /** Adds targets from the same keyframe to the current group (they arrive one by one as the VLM streams). */
    fun add(seed: Seed) {
        commands.add(Command.Add(seed))
    }

    /** Removes the marker now and stops tracking (and drops a seed that hasn't started yet). */
    fun clear() {
        epoch.incrementAndGet()
        commands.add(Command.Stop)
        _state.value = null
    }

    // ---- Everything below runs on the analysis thread. ----

    private class TrackedTarget(val ref: PxBox, val label: String, val isPoint: Boolean) {
        var raw: PxBox = ref
        var smooth: PxBox = ref
    }

    private class Track(val seedId: Int, val keyframeTs: Long, val expand: Float) {
        val targets = ArrayList<TrackedTarget>()
        /** The area the targets span, on the seed frame and now. */
        var refUnion = PxBox(0f, 0f, 0f, 0f)
        var rawUnion = PxBox(0f, 0f, 0f, 0f)
        /** Each point's position on the seed frame (ref) and on the frame stamped [curTs] (cur), as x,y pairs. */
        var ref = FloatArray(0)
        var cur = FloatArray(0)
        var n = 0
        var curTs = 0L
        var initialCount = 0
        /** Seed frame → current frame, row-major 3x3. */
        var model = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        var confidence = 1f
        var status = Status.Tracking
        var failSinceNs = -1L
        var lowSinceNs = -1L
        var lastReGroundNs = -1L
        /** Too few corners to track at all: the marker just stays put. */
        var static = false
        /** The anchor frame fell out of the ring buffer: only a re-ground can bring the marker back. */
        var dead = false
    }

    private var track: Track? = null
    private var seedCounter = 0
    private var fastForwarding = false

    // Reused OpenCV buffers.
    private val cornerMat = MatOfPoint()
    private val prevPts = MatOfPoint2f()
    private val nextPts = MatOfPoint2f()
    private val backPts = MatOfPoint2f()
    private val status1 = MatOfByte()
    private val status2 = MatOfByte()
    private val err = MatOfFloat()
    private val refMat = MatOfPoint2f()
    private val curMat = MatOfPoint2f()
    private val inlierMask = Mat()
    private val mean = MatOfDouble()
    private val std = MatOfDouble()
    private val winSize = Size(LK_WIN.toDouble(), LK_WIN.toDouble())
    private val criteria = TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, 20, 0.03)

    private val frameNs = LongArray(TIMING_WINDOW)
    private var frameCount = 0

    fun onFrame(ring: FrameRingBuffer) {
        if (ring.size == 0) return
        val myEpoch = epoch.get()
        val t0 = System.nanoTime()
        var seeded = false
        while (true) {
            when (val cmd = commands.poll() ?: break) {
                // A seed from a turn that has since been cancelled is dropped.
                is Command.Start -> if (cmd.seed.isCurrent()) { start(cmd.seed, ring); seeded = true }
                is Command.Add -> if (cmd.seed.isCurrent()) { add(cmd.seed, ring); seeded = true }
                Command.Stop -> { track = null; seeded = false }
            }
        }
        if (!seeded) track?.let { if (!it.static && !it.dead) follow(it, ring) }
        val t = track ?: return
        maybeReGround(t, ring)
        if (epoch.get() == myEpoch) {
            _state.value = MarkerState(
                t.seedId,
                t.targets.map { MarkerState.Target(it.smooth, it.label, it.isPoint) },
                ring.width, ring.height, t.confidence, t.status, ring.timestamp(0),
            )
        }
        // Seed frames include the fast-forward, which is logged on its own.
        if (!seeded) recordTiming(System.nanoTime() - t0)
    }

    private fun start(seed: Seed, ring: FrameRingBuffer) {
        val t0 = System.nanoTime()
        var age = ring.ageOf(seed.keyframeTimestampNs)
        if (age < 0) {
            age = ring.size - 1
            Log.w(TAG, "Tracker: keyframe no longer buffered, seeding on the oldest frame (${ring.size} held)")
        }
        val behind = age
        val w = ring.width.toFloat()
        val h = ring.height.toFloat()
        val frame = ring.frame(age)
        val targets = seed.targets.map { TrackedTarget(it.box.clampTo(w, h), it.label, it.isPoint) }
        val (points, expand) = detect(frame, union(targets.map { it.ref }))
        val t = Track(++seedCounter, seed.keyframeTimestampNs, expand)
        t.targets += targets
        t.refUnion = union(targets.map { it.ref })
        t.rawUnion = t.refUnion
        t.curTs = ring.timestamp(age)
        t.cur = points
        t.ref = points.copyOf()
        t.n = points.size / 2
        t.initialCount = t.n
        t.static = t.n < MIN_TRACK_POINTS
        track = t

        // Fast-forward from the keyframe to now, in at most MAX_FF_STEPS steps.
        val stride = max(1, ceil(age / MAX_FF_STEPS.toDouble()).toInt())
        var steps = 0
        fastForwarding = true
        while (age > 0 && !t.static) {
            age = max(0, age - stride)
            step(t, ring, anchorAge = ring.ageOf(t.curTs), nextAge = age)
            steps++
        }
        fastForwarding = false
        t.targets.forEach { it.smooth = it.raw }
        Log.i(
            TAG,
            "Tracker seed #${t.seedId} ${labels(t)}: ${t.initialCount} corners (patch x$expand), " +
                "fast-forward $behind frames in $steps steps, " +
                "${(System.nanoTime() - t0) / 1_000_000} ms, now ${t.n} pts, conf %.2f, ${t.status}".format(t.confidence),
        )
    }

    private fun follow(t: Track, ring: FrameRingBuffer) {
        if (t.curTs == ring.timestamp(0)) return
        val anchorAge = ring.ageOf(t.curTs, toleranceNs = 1_000_000L)
        if (anchorAge < 0) {
            t.dead = true
            t.status = Status.Lost
            Log.i(TAG, "Tracker #${t.seedId}: anchor frame left the buffer, waiting for a re-ground")
            return
        }
        step(t, ring, anchorAge, nextAge = 0)
    }

    /** One LK step from the anchor frame (where [Track.cur] is valid) to the frame [nextAge]. */
    private fun step(t: Track, ring: FrameRingBuffer, anchorAge: Int, nextAge: Int) {
        val nextTs = ring.timestamp(nextAge)
        val ok = anchorAge >= 0 && flow(t, ring.frame(anchorAge), ring.frame(nextAge))
        if (ok) {
            t.curTs = nextTs
            t.failSinceNs = -1L
            t.status = Status.Tracking
            if (t.confidence >= LOW_CONFIDENCE) t.lowSinceNs = -1L else if (t.lowSinceNs < 0) t.lowSinceNs = nextTs
            for (target in t.targets) {
                target.raw = transformBox(t.model, target.ref)
                target.smooth = if (fastForwarding) target.raw else ema(target.smooth, target.raw)
            }
            t.rawUnion = transformBox(t.model, t.refUnion)
            if (t.n < t.initialCount * REDETECT_BELOW) redetect(t, ring.frame(nextAge), t.rawUnion)
        } else {
            t.confidence = 0f
            if (t.failSinceNs < 0) t.failSinceNs = nextTs
            if (t.lowSinceNs < 0) t.lowSinceNs = nextTs
            t.status = if (nextTs - t.failSinceNs < HOLD_NS) Status.Holding else Status.Lost
        }
    }

    /** Moves the points from [prev] to [next] and refits the model. False if tracking failed on this frame. */
    private fun flow(t: Track, prev: Mat, next: Mat): Boolean {
        val n = t.n
        if (n < MIN_TRACK_POINTS) return false
        prevPts.create(n, 1, CvType.CV_32FC2)
        prevPts.put(0, 0, t.cur)
        Video.calcOpticalFlowPyrLK(prev, next, prevPts, nextPts, status1, err, winSize, LK_LEVELS, criteria)
        Video.calcOpticalFlowPyrLK(next, prev, nextPts, backPts, status2, err, winSize, LK_LEVELS, criteria)
        val p1 = FloatArray(2 * n).also { nextPts.get(0, 0, it) }
        val pb = FloatArray(2 * n).also { backPts.get(0, 0, it) }
        val s1 = ByteArray(n).also { status1.get(0, 0, it) }
        val s2 = ByteArray(n).also { status2.get(0, 0, it) }
        val w = next.cols().toFloat()
        val h = next.rows().toFloat()

        // Forward-backward check: a point must come back to where it started.
        val keepRef = FloatArray(2 * n)
        val keepCur = FloatArray(2 * n)
        var k = 0
        for (i in 0 until n) {
            if (s1[i].toInt() == 0 || s2[i].toInt() == 0) continue
            val dx = pb[2 * i] - t.cur[2 * i]
            val dy = pb[2 * i + 1] - t.cur[2 * i + 1]
            if (dx * dx + dy * dy > FB_MAX_PX * FB_MAX_PX) continue
            val x = p1[2 * i]
            val y = p1[2 * i + 1]
            if (x < 0 || y < 0 || x >= w || y >= h) continue
            keepRef[2 * k] = t.ref[2 * i]; keepRef[2 * k + 1] = t.ref[2 * i + 1]
            keepCur[2 * k] = x; keepCur[2 * k + 1] = y
            k++
        }
        if (k < MIN_TRACK_POINTS) return false

        val (model, inliers) = fit(keepRef, keepCur, k, t) ?: return false
        var m = 0
        for (i in 0 until k) {
            if (!inliers[i]) continue
            keepRef[2 * m] = keepRef[2 * i]; keepRef[2 * m + 1] = keepRef[2 * i + 1]
            keepCur[2 * m] = keepCur[2 * i]; keepCur[2 * m + 1] = keepCur[2 * i + 1]
            m++
        }
        val ratio = m.toFloat() / k
        if (m < MIN_TRACK_POINTS || ratio < MIN_INLIER_RATIO) return false
        t.ref = keepRef.copyOf(2 * m)
        t.cur = keepCur.copyOf(2 * m)
        t.n = m
        t.model = model
        t.confidence = ratio * min(1f, m.toFloat() / GOOD_POINTS)
        return true
    }

    /** Seed → current model: homography if it's sane, else similarity, else median shift. Null if nothing fits. */
    private fun fit(ref: FloatArray, cur: FloatArray, k: Int, t: Track): Pair<DoubleArray, BooleanArray>? {
        refMat.create(k, 1, CvType.CV_32FC2); refMat.put(0, 0, ref.copyOf(2 * k))
        curMat.create(k, 1, CvType.CV_32FC2); curMat.put(0, 0, cur.copyOf(2 * k))
        if (k >= MIN_HOMOGRAPHY_POINTS) {
            val hm = Calib3d.findHomography(refMat, curMat, Calib3d.RANSAC, RANSAC_PX, inlierMask)
            val hd = if (hm.empty()) null else DoubleArray(9).also { hm.get(0, 0, it) }
            hm.release()
            if (hd != null && sane(hd, t)) return hd to mask(k)
        }
        val am = Calib3d.estimateAffinePartial2D(refMat, curMat, inlierMask, Calib3d.RANSAC, RANSAC_PX)
        val a = if (am.empty()) null else DoubleArray(6).also { am.get(0, 0, it) }
        am.release()
        if (a != null) {
            val hd = doubleArrayOf(a[0], a[1], a[2], a[3], a[4], a[5], 0.0, 0.0, 1.0)
            if (sane(hd, t)) return hd to mask(k)
        }
        // Median shift of the points against their seed positions.
        val dx = FloatArray(k) { cur[2 * it] - ref[2 * it] }
        val dy = FloatArray(k) { cur[2 * it + 1] - ref[2 * it + 1] }
        val mx = dx.sortedArray()[k / 2]
        val my = dy.sortedArray()[k / 2]
        val inliers = BooleanArray(k) { abs(dx[it] - mx) <= RANSAC_PX && abs(dy[it] - my) <= RANSAC_PX }
        val hd = doubleArrayOf(1.0, 0.0, mx.toDouble(), 0.0, 1.0, my.toDouble(), 0.0, 0.0, 1.0)
        return if (sane(hd, t)) hd to inliers else null
    }

    private fun mask(k: Int): BooleanArray {
        val b = ByteArray(k).also { inlierMask.get(0, 0, it) }
        return BooleanArray(k) { b[it].toInt() != 0 }
    }

    /** Rejects models that fold, flip or jump the targets: convex quad, plausible size change, no teleporting. */
    private fun sane(hd: DoubleArray, t: Track): Boolean {
        val c = boxCorners(t.refUnion)
        val q = DoubleArray(8)
        for (i in 0 until 4) {
            val x = c[2 * i]
            val y = c[2 * i + 1]
            val wq = hd[6] * x + hd[7] * y + hd[8]
            if (wq <= 1e-6) return false
            q[2 * i] = (hd[0] * x + hd[1] * y + hd[2]) / wq
            q[2 * i + 1] = (hd[3] * x + hd[4] * y + hd[5]) / wq
        }
        var sign = 0
        for (i in 0 until 4) {
            val ax = q[2 * ((i + 1) % 4)] - q[2 * i]
            val ay = q[2 * ((i + 1) % 4) + 1] - q[2 * i + 1]
            val bx = q[2 * ((i + 2) % 4)] - q[2 * ((i + 1) % 4)]
            val by = q[2 * ((i + 2) % 4) + 1] - q[2 * ((i + 1) % 4) + 1]
            val cross = ax * by - ay * bx
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0 || (sign != 0 && s != sign)) return false
            sign = s
        }
        val area = abs(shoelace(q))
        val refArea = t.refUnion.width.toDouble() * t.refUnion.height
        if (refArea <= 0 || area / refArea !in MIN_SCALE_AREA..MAX_SCALE_AREA) return false
        val prev = t.rawUnion
        val prevArea = prev.width.toDouble() * prev.height
        if (prevArea > 0 && area / prevArea !in (1 / MAX_STEP_AREA)..MAX_STEP_AREA) return false
        val cx = (q[0] + q[2] + q[4] + q[6]) / 4
        val cy = (q[1] + q[3] + q[5] + q[7]) / 4
        val jump = max(abs(cx - prev.centerX), abs(cy - prev.centerY))
        return jump <= MAX_STEP_JUMP * max(prev.width, prev.height) + MAX_STEP_JUMP_PX
    }

    private fun transformBox(hd: DoubleArray, box: PxBox): PxBox {
        val c = boxCorners(box)
        var l = Float.MAX_VALUE; var tp = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until 4) {
            val x = c[2 * i]
            val y = c[2 * i + 1]
            val wq = hd[6] * x + hd[7] * y + hd[8]
            val qx = ((hd[0] * x + hd[1] * y + hd[2]) / wq).toFloat()
            val qy = ((hd[3] * x + hd[4] * y + hd[5]) / wq).toFloat()
            l = min(l, qx); r = max(r, qx); tp = min(tp, qy); b = max(b, qy)
        }
        return PxBox(l, tp, r, b)
    }

    /** Adds fresh corners inside [region] (current frame), away from the points we still have. */
    private fun redetect(t: Track, frame: Mat, region: PxBox) {
        val inv = invert(t.model) ?: return
        val (fresh, _) = detect(frame, region.clampTo(frame.cols().toFloat(), frame.rows().toFloat()), fixedExpand = t.expand)
        val ref = t.ref.toMutableList()
        val cur = t.cur.toMutableList()
        var added = 0
        for (i in 0 until fresh.size / 2) {
            if (ref.size / 2 >= MAX_CORNERS) break
            val x = fresh[2 * i]
            val y = fresh[2 * i + 1]
            var near = false
            for (j in 0 until cur.size / 2) {
                val dx = cur[2 * j] - x
                val dy = cur[2 * j + 1] - y
                if (dx * dx + dy * dy < MIN_CORNER_DIST * MIN_CORNER_DIST) { near = true; break }
            }
            if (near) continue
            val wq = inv[6] * x + inv[7] * y + inv[8]
            if (abs(wq) < 1e-6) continue
            cur += x; cur += y
            ref += ((inv[0] * x + inv[1] * y + inv[2]) / wq).toFloat()
            ref += ((inv[3] * x + inv[4] * y + inv[5]) / wq).toFloat()
            added++
        }
        t.ref = ref.toFloatArray()
        t.cur = cur.toFloatArray()
        t.n = t.cur.size / 2
        t.initialCount = t.n
        if (!fastForwarding) Log.d(TAG, "Tracker #${t.seedId}: re-detected, +$added corners, now ${t.n}")
    }

    /**
     * Corners inside [box], widening the patch until there are enough ([MIN_SEED_POINTS]); returns the points
     * (x,y pairs, frame px) and the widening used.
     */
    private fun detect(frame: Mat, box: PxBox, fixedExpand: Float? = null): Pair<FloatArray, Float> {
        val w = frame.cols().toFloat()
        val h = frame.rows().toFloat()
        var best = FloatArray(0) to 1f
        for (f in fixedExpand?.let { listOf(it) } ?: SEED_EXPANSIONS) {
            val r = box.expand(f).clampTo(w, h)
            val x0 = r.left.toInt()
            val y0 = r.top.toInt()
            val x1 = r.right.toInt()
            val y1 = r.bottom.toInt()
            if (x1 - x0 < 8 || y1 - y0 < 8) continue
            val roi = frame.submat(y0, y1, x0, x1)
            Imgproc.goodFeaturesToTrack(roi, cornerMat, MAX_CORNERS, CORNER_QUALITY, MIN_CORNER_DIST.toDouble())
            roi.release()
            val raw = cornerMat.toArray()
            val pts = FloatArray(raw.size * 2)
            raw.forEachIndexed { i, p -> pts[2 * i] = (p.x + x0).toFloat(); pts[2 * i + 1] = (p.y + y0).toFloat() }
            if (pts.size > best.first.size) best = pts to f
            if (raw.size >= MIN_SEED_POINTS) break
        }
        return best
    }

    private fun maybeReGround(t: Track, ring: FrameRingBuffer) {
        if (t.static || t.lowSinceNs < 0) return
        val now = ring.timestamp(0)
        if (now - t.lowSinceNs < REGROUND_AFTER_NS) return
        if (t.lastReGroundNs >= 0 && now - t.lastReGroundNs < REGROUND_EVERY_NS) return
        t.lastReGroundNs = now
        // A covered or pitch-dark camera can't be grounded; wait until there's something to see.
        Core.meanStdDev(ring.frame(0), mean, std)
        if (std.toArray()[0] < BLIND_STD) {
            Log.i(TAG, "Tracker #${t.seedId}: lost, but the view is blank; not re-grounding yet")
            return
        }
        Log.i(TAG, "Tracker #${t.seedId}: low confidence for ${(now - t.lowSinceNs) / 1_000_000} ms, asking for a re-ground")
        onReGround(t.targets.map { it.label }.distinct())
    }

    /**
     * Streams a target into the current group. It comes from the same keyframe as the group's seed, so its box
     * is already in seed-frame coordinates; corners around where it is now are added (mapped back through the
     * model). A target for a different keyframe, or with no group yet, starts a new group.
     */
    private fun add(seed: Seed, ring: FrameRingBuffer) {
        val t = track
        if (t == null || t.keyframeTs != seed.keyframeTimestampNs) {
            start(seed, ring)
            return
        }
        val w = ring.width.toFloat()
        val h = ring.height.toFloat()
        val anchorAge = ring.ageOf(t.curTs, toleranceNs = 1_000_000L)
        for (s in seed.targets) {
            val target = TrackedTarget(s.box.clampTo(w, h), s.label, s.isPoint)
            target.raw = transformBox(t.model, target.ref)
            target.smooth = target.raw
            t.targets += target
            if (anchorAge >= 0) redetect(t, ring.frame(anchorAge), target.raw)
        }
        t.refUnion = union(t.targets.map { it.ref })
        t.rawUnion = transformBox(t.model, t.refUnion)
        if (t.static && t.n >= MIN_TRACK_POINTS) t.static = false
        Log.i(TAG, "Tracker #${t.seedId}: +${seed.targets.size} target(s), now ${t.targets.size}, ${t.n} pts")
    }

    private fun labels(t: Track) = t.targets.groupingBy { it.label }.eachCount().entries.joinToString { (l, n) -> if (n > 1) "$n×\"$l\"" else "\"$l\"" }

    private fun recordTiming(ns: Long) {
        if (track == null) return
        frameNs[frameCount % TIMING_WINDOW] = ns
        frameCount++
        if (frameCount % TIMING_WINDOW == 0) {
            val sorted = frameNs.sortedArray()
            Log.i(
                TAG,
                "Tracker: p50 %.2f ms, p95 %.2f ms over $TIMING_WINDOW frames (%d pts, conf %.2f, %s)".format(
                    sorted[TIMING_WINDOW / 2] / 1e6, sorted[(TIMING_WINDOW * 95) / 100] / 1e6,
                    track?.n ?: 0, track?.confidence ?: 0f, track?.status,
                ),
            )
        }
    }

    private companion object {
        const val MAX_CORNERS = 80
        const val CORNER_QUALITY = 0.01
        const val MIN_CORNER_DIST = 5f
        const val MIN_SEED_POINTS = 20
        /** Widening steps for a patch with few corners (1.3 = the ~30% of CLAUDE.md §10, then more). */
        val SEED_EXPANSIONS = listOf(1f, 1.3f, 1.8f, 2.5f)
        const val MIN_TRACK_POINTS = 6
        const val MIN_HOMOGRAPHY_POINTS = 8
        const val GOOD_POINTS = 15f
        const val REDETECT_BELOW = 0.5f
        const val LK_WIN = 21
        const val LK_LEVELS = 3
        const val FB_MAX_PX = 1.5f
        const val RANSAC_PX = 3.0
        const val MIN_INLIER_RATIO = 0.5f
        const val LOW_CONFIDENCE = 0.3f
        const val EMA_ALPHA = 0.4f
        /** Fast-forward in at most this many LK steps (bigger strides when the VLM was slow). */
        const val MAX_FF_STEPS = 45
        const val MIN_SCALE_AREA = 1.0 / 16
        const val MAX_SCALE_AREA = 16.0
        const val MAX_STEP_AREA = 2.0
        const val MAX_STEP_JUMP = 0.75f
        const val MAX_STEP_JUMP_PX = 12f
        const val HOLD_NS = 500_000_000L
        const val REGROUND_AFTER_NS = 1_000_000_000L
        const val REGROUND_EVERY_NS = 3_000_000_000L
        const val BLIND_STD = 12.0
        const val TIMING_WINDOW = 60

        fun union(boxes: List<PxBox>) = PxBox(
            boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom },
        )

        fun boxCorners(b: PxBox) = doubleArrayOf(
            b.left.toDouble(), b.top.toDouble(), b.right.toDouble(), b.top.toDouble(),
            b.right.toDouble(), b.bottom.toDouble(), b.left.toDouble(), b.bottom.toDouble(),
        )

        fun shoelace(q: DoubleArray): Double {
            var s = 0.0
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                s += q[2 * i] * q[2 * j + 1] - q[2 * j] * q[2 * i + 1]
            }
            return s / 2
        }

        fun ema(prev: PxBox, next: PxBox): PxBox = PxBox.centered(
            prev.centerX + EMA_ALPHA * (next.centerX - prev.centerX),
            prev.centerY + EMA_ALPHA * (next.centerY - prev.centerY),
            prev.width + EMA_ALPHA * (next.width - prev.width),
            prev.height + EMA_ALPHA * (next.height - prev.height),
        )

        fun invert(m: DoubleArray): DoubleArray? {
            val det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])
            if (abs(det) < 1e-12) return null
            val d = 1 / det
            return doubleArrayOf(
                (m[4] * m[8] - m[5] * m[7]) * d, (m[2] * m[7] - m[1] * m[8]) * d, (m[1] * m[5] - m[2] * m[4]) * d,
                (m[5] * m[6] - m[3] * m[8]) * d, (m[0] * m[8] - m[2] * m[6]) * d, (m[2] * m[3] - m[0] * m[5]) * d,
                (m[3] * m[7] - m[4] * m[6]) * d, (m[1] * m[6] - m[0] * m[7]) * d, (m[0] * m[4] - m[1] * m[3]) * d,
            )
        }
    }
}
