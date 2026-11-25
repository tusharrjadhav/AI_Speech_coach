package com.example.speechcoach.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt

@Serializable
data class QuietGap(val startMillis: Long, val endMillis: Long)

@Serializable
data class RecordingEvidence(val gaps: List<QuietGap>, val lowLevel: Boolean, val clipping: Boolean)

/** Fixed 20 ms PCM windows; quiet gaps are signal-level estimates, not linguistic pauses. */
class PcmAnalyzer(private val sampleRate: Int) {
    init { require(sampleRate >= 50) }
    private val window = sampleRate / 50
    private var count = 0L
    private var inWindow = 0
    private var squareSum = 0.0
    private var peakRms = 0.0
    private var nearRail = 0L
    private var lastActiveEnd: Long? = null
    private val gaps = mutableListOf<QuietGap>()

    fun add(sample: Short) {
        val level = sample / 32768.0
        if (abs(level) >= 0.999) nearRail++
        squareSum += level * level
        count++
        if (++inWindow == window) finishWindow()
    }

    private fun finishWindow() {
        val rms = sqrt(squareSum / inWindow)
        peakRms = maxOf(peakRms, rms)
        if (rms >= 0.01) {
            val start = (count - inWindow) * 1000 / sampleRate
            lastActiveEnd?.let { if (start - it >= 500) gaps.add(QuietGap(it, start)) }
            lastActiveEnd = count * 1000 / sampleRate
        }
        inWindow = 0
        squareSum = 0.0
    }

    fun finish(): RecordingEvidence {
        require(count > 0) { "No PCM samples to analyze." }
        if (inWindow > 0) finishWindow()
        return RecordingEvidence(gaps.toList(), peakRms < 0.02, nearRail.toDouble() / count >= 0.001)
    }
}
