package com.freebuds6ipro.dsp

data class DspSettings(
    val hpfOn: Boolean = true, val hpfHz: Float = 90f,
    val nsOn: Boolean = true, val nsDb: Float = 10f,
    val agcOn: Boolean = true, val agcTargetDb: Float = -21f, val agcMaxGainDb: Float = 12f,
    val compOn: Boolean = true, val compThrDb: Float = -20f, val compRatio: Float = 2.5f, val compMakeupDb: Float = 0f,
    val eqOn: Boolean = true, val presenceDb: Float = 3f, val mudDb: Float = -1f,
    val limOn: Boolean = true, val limCeilDb: Float = -1f
)

/** Order: HPF -> Noise suppression -> AGC -> Compressor -> EQ (clarity) -> Limiter. */
class DspPipeline(s: DspSettings, fs: Int) {
    private val stages: List<Stage> = buildList {
        if (s.hpfOn) add(HighPass(fs, s.hpfHz))
        if (s.nsOn) add(NoiseSuppressor(fs, s.nsDb))
        if (s.agcOn) add(Agc(fs, s.agcTargetDb, s.agcMaxGainDb))
        if (s.compOn) add(Compressor(fs, s.compThrDb, s.compRatio, s.compMakeupDb))
        if (s.eqOn) add(Equalizer(fs, listOf(EqBand(300f, 1f, s.mudDb), EqBand(3000f, 0.9f, s.presenceDb))))
        if (s.limOn) add(Limiter(fs, s.limCeilDb))
    }
    fun process(x: FloatArray): FloatArray { val y = x.copyOf(); for (st in stages) st.process(y, y.size); return y }
}

object Presets {
    val all: Map<String, DspSettings> = linkedMapOf(
        "natural" to DspSettings(hpfHz = 80f, nsDb = 6f, agcTargetDb = -22f, agcMaxGainDb = 8f, compThrDb = -20f, compRatio = 2f, presenceDb = 1f, mudDb = 0f),
        "clear" to DspSettings(hpfHz = 100f, nsDb = 12f, agcTargetDb = -20f, agcMaxGainDb = 12f, compRatio = 3f, presenceDb = 4f, mudDb = -2f),
        "lowboost" to DspSettings(hpfHz = 90f, nsDb = 9f, agcTargetDb = -18f, agcMaxGainDb = 20f, compThrDb = -24f, compRatio = 3f, compMakeupDb = 2f, presenceDb = 3f),
        "noisy" to DspSettings(hpfHz = 150f, nsDb = 20f, agcTargetDb = -20f, agcMaxGainDb = 10f, compRatio = 4f, presenceDb = 5f, mudDb = -3f),
        "balanced" to DspSettings(hpfHz = 90f, nsDb = 10f, agcTargetDb = -21f, agcMaxGainDb = 12f, compRatio = 2.5f, presenceDb = 3f, mudDb = -1f)
    )
}
