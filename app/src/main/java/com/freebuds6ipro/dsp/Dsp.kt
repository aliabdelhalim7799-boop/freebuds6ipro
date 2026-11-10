package com.freebuds6ipro.dsp

import kotlin.math.*

fun dbToLin(db: Float): Float = 10f.pow(db / 20f)
fun linToDb(x: Float): Float = 20f * log10(max(x, 1e-9f))

/** Mono float processing stage (samples in [-1,1]); in-place, stateful, chunk-size independent. */
interface Stage { fun process(b: FloatArray, n: Int = b.size) }

class Biquad(private val b0: Double, private val b1: Double, private val b2: Double,
             private val a1: Double, private val a2: Double) {
    private var z1 = 0.0
    private var z2 = 0.0
    fun process(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + z1
        z1 = b1 * xd - a1 * y + z2
        z2 = b2 * xd - a2 * y
        return y.toFloat()
    }
    companion object {
        fun highpass(fs: Int, f: Double, q: Double = 0.7071): Biquad {
            val w = 2 * PI * f / fs; val c = cos(w); val al = sin(w) / (2 * q); val a0 = 1 + al
            return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - al) / a0)
        }
        fun peaking(fs: Int, f: Double, q: Double, gainDb: Double): Biquad {
            val a = 10.0.pow(gainDb / 40); val w = 2 * PI * f / fs; val c = cos(w)
            val al = sin(w) / (2 * q); val a0 = 1 + al / a
            return Biquad((1 + al * a) / a0, -2 * c / a0, (1 - al * a) / a0, -2 * c / a0, (1 - al / a) / a0)
        }
    }
}

/** 4th-order high-pass (two cascaded biquads): removes rumble / wind / handling noise. */
class HighPass(fs: Int, hz: Float) : Stage {
    private val a = Biquad.highpass(fs, hz.toDouble())
    private val b = Biquad.highpass(fs, hz.toDouble())
    override fun process(b: FloatArray, n: Int) { for (i in 0 until n) b[i] = this.b.process(a.process(b[i])) }
}

/** Noise suppression: adaptive noise-floor tracking + energy VAD + smoothed downward expansion. */
class NoiseSuppressor(private val fs: Int, private val reductionDb: Float) : Stage {
    private val frame = fs / 100
    private var acc = 0.0; private var cnt = 0
    private var floor = -1.0; private var gain = 1f; private var target = 1f
    var vad = false; private set
    private val atk = 1f - exp(-1f / (0.005f * fs))
    private val rel = 1f - exp(-1f / (0.12f * fs))
    override fun process(b: FloatArray, n: Int) {
        for (i in 0 until n) {
            val x = b[i]; acc += x * x; cnt++
            if (cnt >= frame) {
                val r = sqrt(acc / cnt); acc = 0.0; cnt = 0
                floor = when { floor < 0 -> r; r < floor -> r; else -> floor + (r - floor) * 0.0005 }
                floor = max(floor, 1e-5)
                vad = r > floor * 3.0
                target = if (vad) 1f else dbToLin(-reductionDb)
            }
            gain += (target - gain) * (if (target > gain) atk else rel)
            b[i] = x * gain
        }
    }
}

/** Automatic gain control: slow, gated (never amplifies silence/noise), bounded gain. */
class Agc(private val fs: Int, private val targetDb: Float, private val maxGainDb: Float,
          private val gateDb: Float = -55f) : Stage {
    private var env = 0.0; private var gainDb = 0f; private var cur = 1f
    private val envA = 1.0 - exp(-1.0 / (0.1 * fs))
    private val block = 64
    private val atk = 1f - exp(-block / (0.03f * fs))
    private val rel = 1f - exp(-block / (0.5f * fs))
    override fun process(b: FloatArray, n: Int) {
        var i = 0
        while (i < n) {
            val end = min(n, i + block)
            for (k in i until end) env += (b[k] * b[k] - env) * envA
            val level = (10 * log10(env + 1e-12)).toFloat()
            val desired = if (level > gateDb) (targetDb - level).coerceIn(-12f, maxGainDb) else gainDb
            gainDb += (desired - gainDb) * (if (desired < gainDb) atk else rel)
            val step = (dbToLin(gainDb) - cur) / (end - i)
            for (k in i until end) { cur += step; b[k] *= cur }
            i = end
        }
    }
}

class Compressor(private val fs: Int, private val thrDb: Float, private val ratio: Float,
                 private val makeupDb: Float) : Stage {
    private var env = -100f; private var gr = 0f
    private val atk = 1f - exp(-1f / (0.01f * fs))
    private val rel = 1f - exp(-1f / (0.15f * fs))
    override fun process(b: FloatArray, n: Int) {
        for (i in 0 until n) {
            val x = b[i]; val l = linToDb(abs(x))
            env += (l - env) * (if (l > env) atk else rel)
            val over = env - thr()
            val t = if (over > 0) over * (1f - 1f / ratio) else 0f
            gr += (t - gr) * (if (t > gr) atk else rel)
            b[i] = x * dbToLin(makeupDb - gr)
        }
    }
    private fun thr() = thrDb
}

/** Sample-accurate limiter: gain is always <= ceiling/|x|, so |y| <= ceiling (no clipping). */
class Limiter(fs: Int, ceilingDb: Float) : Stage {
    private val c = dbToLin(ceilingDb)
    private var g = 1f
    private val rel = 1f - exp(-1f / (0.05f * fs))
    override fun process(b: FloatArray, n: Int) {
        for (i in 0 until n) {
            val x = b[i]; val a = abs(x)
            val t = if (a > c) c / a else 1f
            g = if (t < g) t else g + (1f - g) * rel
            if (g > t) g = t
            b[i] = x * g
        }
    }
}

data class EqBand(val hz: Float, val q: Float, val gainDb: Float)

/** Parametric EQ: cascaded peaking biquads (used for voice clarity: presence boost / mud cut). */
class Equalizer(fs: Int, bands: List<EqBand>) : Stage {
    private val f = bands.filter { it.gainDb != 0f }.map { Biquad.peaking(fs, it.hz.toDouble(), it.q.toDouble(), it.gainDb.toDouble()) }
    override fun process(b: FloatArray, n: Int) {
        for (i in 0 until n) { var y = b[i]; for (s in f) y = s.process(y); b[i] = y }
    }
}
