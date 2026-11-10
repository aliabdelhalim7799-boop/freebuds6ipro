package com.freebuds6ipro.dsp

import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import kotlin.math.*

class DspTest {
    private val fs = 16000
    private fun sine(f: Double, amp: Float, sec: Double = 3.0) =
        FloatArray((fs * sec).toInt()) { (amp * sin(2 * PI * f * it / fs)).toFloat() }
    private fun noise(amp: Float, sec: Double = 3.0): FloatArray { val r = Random(1); return FloatArray((fs * sec).toInt()) { amp * (r.nextFloat() * 2 - 1) } }
    private fun tailDb(x: FloatArray) = Metrics.rmsDb(x, x.size * 2 / 3, x.size)
    private fun run(s: Stage, x: FloatArray) = x.copyOf().also { s.process(it, it.size) }

    @Test fun highpassCutsLowKeepsMid() {
        val lo = sine(40.0, 0.5f); val mid = sine(1000.0, 0.5f)
        assertTrue(tailDb(run(HighPass(fs, 100f), lo)) < tailDb(lo) - 12)
        assertEquals(tailDb(mid), tailDb(run(HighPass(fs, 100f), mid)), 1f)
    }
    @Test fun noiseSuppressorReducesStationaryNoise() {
        val x = noise(0.01f)
        assertTrue(tailDb(run(NoiseSuppressor(fs, 18f), x)) < tailDb(x) - 6)
    }
        @Test fun noiseSuppressorKeepsSpeechLikeBursts() {
        val seg = fs / 2
        val r = Random(2)
        val x = FloatArray(seg * 8) { i ->
            if ((i / seg) % 2 == 0) (0.3 * sin(2 * PI * 500 * i / fs)).toFloat()
            else 0.002f * (r.nextFloat() * 2 - 1)
        }
        val y = run(NoiseSuppressor(fs, 18f), x)
        val from = seg * 4 + fs / 20
        val to = seg * 5
        assertEquals(Metrics.rmsDb(x, from, to), Metrics.rmsDb(y, from, to), 1.5f)
    }
    @Test fun agcRaisesQuietSignalWithinMaxGain() {
        val x = sine(1000.0, 0.01f, 6.0); val y = run(Agc(fs, -20f, 18f), x)
        val gain = tailDb(y) - tailDb(x)
        assertTrue("gain=$gain", gain > 10f && gain <= 18.5f)
    }
    @Test fun agcDoesNotAmplifySilenceBelowGate() {
        val x = sine(1000.0, 0.0005f, 4.0) // about -69 dBFS, below the gate
        assertEquals(tailDb(x), tailDb(run(Agc(fs, -20f, 18f), x)), 1f)
    }
    @Test fun compressorReducesLoudSignal() {
        val x = sine(1000.0, 0.9f)
        assertTrue(tailDb(run(Compressor(fs, -20f, 4f, 0f), x)) < tailDb(x) - 6)
    }
    @Test fun limiterNeverExceedsCeiling() {
        val x = sine(300.0, 2.5f); val y = run(Limiter(fs, -1f), x)
        assertTrue(y.maxOf { abs(it) } <= dbToLin(-1f) + 1e-3f)
    }
    @Test fun equalizerBoostsBand() {
        val x = sine(3000.0, 0.1f); val y = run(Equalizer(fs, listOf(EqBand(3000f, 1f, 6f))), x)
        assertEquals(6f, tailDb(y) - tailDb(x), 1.5f)
    }
    @Test fun allPresetsProduceFiniteUnclippedOutput() {
        val x = noise(0.02f).also { a -> val s = sine(220.0, 0.2f); for (i in a.indices) a[i] += s[i] }
        for ((k, p) in Presets.all) {
            val y = DspPipeline(p, fs).process(x)
            assertTrue(k, y.all { it.isFinite() && abs(it) <= dbToLin(p.limCeilDb) + 1e-3f })
        }
    }
    @Test fun metricsDetectClipping() {
        val x = FloatArray(1000) { 1f }
        assertEquals(1000, Metrics.clipCount(x))
        assertEquals(0f, Metrics.peakDb(x), 0.01f)
    }
    @Test fun sampleRateIndependence() {
        for (r in listOf(8000, 16000, 44100, 48000)) {
            val x = FloatArray(r * 2) { (0.3 * sin(2 * PI * 1000 * it / r)).toFloat() }
            val y = DspPipeline(DspSettings(), r).process(x)
            assertTrue(y.all { it.isFinite() })
        }
    }
}
