package com.freebuds6ipro.dsp

import kotlin.math.*

object Metrics {
    data class Result(val rmsDb: Float, val peakDb: Float, val clipCount: Int,
                      val noiseFloorDb: Float, val speechDb: Float, val hissBandDb: Float) {
        /** Rough SNR: loud-frame level minus quiet-frame level. An estimate, not a lab measurement. */
        val snrDb get() = speechDb - noiseFloorDb
    }

    fun rmsDb(x: FloatArray, from: Int = 0, to: Int = x.size): Float {
        if (to <= from) return -120f
        var s = 0.0; for (i in from until to) s += x[i] * x[i]
        return (10 * log10(s / (to - from) + 1e-12)).toFloat()
    }
    fun peakDb(x: FloatArray): Float = linToDb(x.maxOfOrNull { abs(it) } ?: 0f)
    fun clipCount(x: FloatArray) = x.count { abs(it) >= 0.999f }

    private fun frames(x: FloatArray, fs: Int): List<Float> {
        val n = fs / 50
        return (0 until x.size / n).map { rmsDb(x, it * n, (it + 1) * n) }.sorted()
    }
    private fun pct(s: List<Float>, p: Float) = if (s.isEmpty()) -120f else s[((s.size - 1) * p).toInt()]

    fun analyze(x: FloatArray, fs: Int): Result {
        val f = frames(x, fs)
        val hp = HighPass(fs, 2000f); val h = x.copyOf(); hp.process(h, h.size)
        return Result(rmsDb(x), peakDb(x), clipCount(x), pct(f, 0.10f), pct(f, 0.90f), rmsDb(h))
    }
}
