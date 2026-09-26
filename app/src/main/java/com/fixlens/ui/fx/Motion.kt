package com.fixlens.ui.fx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Pure timing curves for effects driven by one frame clock: every element of a timeline is a function of
// "ms since its cue", so nothing needs its own Animatable and a whole marker group redraws from one clock read.

/** 0..1 progress of a window [startMs, startMs + durationMs] at [elapsedMs]. */
internal fun window(elapsedMs: Float, startMs: Float, durationMs: Float): Float =
    ((elapsedMs - startMs) / durationMs).coerceIn(0f, 1f)

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

internal fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3)

internal fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Slow launch, fast arrival: a projectile speeding into its target. */
internal fun easeInSine(t: Float) = 1f - cos(t * PI.toFloat() / 2f)

/** Back-out: shoots ~[overshoot] past 1 and settles. For small pops (a chip). */
internal fun easeOutBack(t: Float, overshoot: Float = 1.7f): Float {
    val c3 = overshoot + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + overshoot * u * u
}

/**
 * Step response of an under-damped spring: 0 at t=0, overshoots, rings and settles at 1. [seconds] since the
 * cue; [damping] 0..1 (0.55 ≈ 13% overshoot), [omega] rad/s (≈ 4 / (damping·omega) s to settle).
 */
internal fun springOut(seconds: Float, damping: Float = 0.55f, omega: Float = 15f): Float {
    if (seconds <= 0f) return 0f
    val wd = omega * sqrt(1f - damping * damping)
    val decay = exp(-damping * omega * seconds)
    return 1f - decay * (cos(wd * seconds) + damping / sqrt(1f - damping * damping) * sin(wd * seconds))
}

/** Deterministic 0..1 noise for a seed, so particles are random-looking but identical every frame. */
internal fun hash01(seed: Int): Float {
    var x = seed * 0x27d4eb2d
    x = x xor (x ushr 15)
    x *= 0x2c1b3c6d
    x = x xor (x ushr 12)
    return (x and 0xFFFFFF) / 16777216f
}
