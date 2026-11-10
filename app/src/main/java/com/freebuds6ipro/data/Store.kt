package com.freebuds6ipro.data

import android.content.Context
import com.freebuds6ipro.dsp.DspSettings
import com.freebuds6ipro.dsp.Presets
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.f(k: String, d: Float) = optDouble(k, d.toDouble()).toFloat()

object SettingsJson {
    fun to(s: DspSettings) = JSONObject().apply {
        put("hpfOn", s.hpfOn); put("hpfHz", s.hpfHz.toDouble())
        put("nsOn", s.nsOn); put("nsDb", s.nsDb.toDouble())
        put("agcOn", s.agcOn); put("agcTargetDb", s.agcTargetDb.toDouble()); put("agcMaxGainDb", s.agcMaxGainDb.toDouble())
        put("compOn", s.compOn); put("compThrDb", s.compThrDb.toDouble()); put("compRatio", s.compRatio.toDouble()); put("compMakeupDb", s.compMakeupDb.toDouble())
        put("eqOn", s.eqOn); put("presenceDb", s.presenceDb.toDouble()); put("mudDb", s.mudDb.toDouble())
        put("limOn", s.limOn); put("limCeilDb", s.limCeilDb.toDouble())
    }
    fun from(o: JSONObject, d: DspSettings = DspSettings()) = DspSettings(
        o.optBoolean("hpfOn", d.hpfOn), o.f("hpfHz", d.hpfHz),
        o.optBoolean("nsOn", d.nsOn), o.f("nsDb", d.nsDb),
        o.optBoolean("agcOn", d.agcOn), o.f("agcTargetDb", d.agcTargetDb), o.f("agcMaxGainDb", d.agcMaxGainDb),
        o.optBoolean("compOn", d.compOn), o.f("compThrDb", d.compThrDb), o.f("compRatio", d.compRatio), o.f("compMakeupDb", d.compMakeupDb),
        o.optBoolean("eqOn", d.eqOn), o.f("presenceDb", d.presenceDb), o.f("mudDb", d.mudDb),
        o.optBoolean("limOn", d.limOn), o.f("limCeilDb", d.limCeilDb)
    )
}

data class LabResult(val kind: String, val mode: String, val side: String,
                     val noiseFloorDb: Float, val hissDb: Float, val source: String, val time: Long)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("fbp", Context.MODE_PRIVATE)

    fun loadCurrent(): DspSettings =
        p.getString("cur", null)?.let { runCatching { SettingsJson.from(JSONObject(it)) }.getOrNull() }
            ?: Presets.all.getValue("clear")
    fun saveCurrent(s: DspSettings) = p.edit().putString("cur", SettingsJson.to(s).toString()).apply()

    private fun profiles() = JSONObject(p.getString("profiles", "{}") ?: "{}")
    fun names(): List<String> = profiles().keys().asSequence().toList()
    fun saveProfile(name: String, s: DspSettings) {
        val o = profiles(); o.put(name, SettingsJson.to(s)); p.edit().putString("profiles", o.toString()).apply()
    }
    fun loadProfile(name: String): DspSettings? = profiles().optJSONObject(name)?.let { SettingsJson.from(it) }
    fun deleteProfile(name: String) { val o = profiles(); o.remove(name); p.edit().putString("profiles", o.toString()).apply() }

    fun exportProfile(name: String, s: DspSettings) =
        JSONObject().put("app", "FreeBuds6iPro").put("name", name).put("settings", SettingsJson.to(s)).toString(2)
    fun importProfile(text: String): Pair<String, DspSettings>? = runCatching {
        val o = JSONObject(text)
        o.getString("name") to SettingsJson.from(o.getJSONObject("settings"))
    }.getOrNull()

    fun lab(): List<LabResult> = runCatching {
        val a = JSONArray(p.getString("lab", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it)
            LabResult(o.getString("kind"), o.getString("mode"), o.getString("side"),
                o.getDouble("nf").toFloat(), o.getDouble("hiss").toFloat(), o.getString("src"), o.getLong("t")) }
    }.getOrDefault(emptyList())
    fun saveLab(l: List<LabResult>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("kind", it.kind).put("mode", it.mode).put("side", it.side)
            .put("nf", it.noiseFloorDb.toDouble()).put("hiss", it.hissDb.toDouble()).put("src", it.source).put("t", it.time)) }
        p.edit().putString("lab", a.toString()).apply()
    }
}
