package com.freebuds6ipro

import android.app.Application
import android.os.Build
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freebuds6ipro.audio.*
import com.freebuds6ipro.data.*
import com.freebuds6ipro.dsp.*
import com.freebuds6ipro.ui.tr
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val bt = BtManager(app)
    private val recorder = MicRecorder(app)
    private val store = Store(app)

    var settings by mutableStateOf(store.loadCurrent()); private set
    var preset by mutableStateOf<String?>("clear"); private set
    var viaSco by mutableStateOf(false)
    var busy by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set
    var level by mutableFloatStateOf(-90f); private set
    var route by mutableStateOf<String?>(null); private set
    var scoStarted by mutableStateOf<Boolean?>(null); private set
    var message by mutableStateOf<String?>(null)
    var orig by mutableStateOf<FloatArray?>(null); private set
    var proc by mutableStateOf<FloatArray?>(null); private set
    var origM by mutableStateOf<Metrics.Result?>(null); private set
    var procM by mutableStateOf<Metrics.Result?>(null); private set
    private var fs = 16000
    var labResults by mutableStateOf(store.lab()); private set
    var profiles by mutableStateOf(store.names()); private set
    private var procJob: Job? = null

    fun rawSupported() = recorder.rawSupported()

    fun update(s: DspSettings, fromPreset: String? = null) {
        settings = s; preset = fromPreset; store.saveCurrent(s); reprocess()
    }
    fun applyPreset(k: String) = update(Presets.all.getValue(k), k)

    private fun reprocess() {
        procJob?.cancel()
        procJob = viewModelScope.launch(Dispatchers.Default) {
            val o = orig ?: return@launch
            val p = DspPipeline(settings, fs).process(o)
            proc = p; procM = Metrics.analyze(p, fs)
        }
    }

    fun record() = viewModelScope.launch {
        busy = true; message = null
        try {
            val r = recorder.record(8, if (viaSco) Source.SCO else Source.PHONE) { level = it }
            orig = r.samples; fs = r.fs; route = r.route; scoStarted = if (viaSco) r.scoStarted else null
            origM = Metrics.analyze(r.samples, r.fs); reprocess()
            if (viaSco && !r.scoStarted) message = tr("لم يتصل SCO؛ المصدر الفعلي: ${r.route}", "SCO did not connect; actual source: ${r.route}")
        } catch (e: Exception) { message = e.message } finally { busy = false; level = -90f }
    }

    fun play(processed: Boolean) = viewModelScope.launch {
        val x = (if (processed) proc else orig) ?: return@launch
        playing = true; try { Player.play(x, fs) } catch (e: Exception) { message = e.message } finally { playing = false }
    }

    fun measureLab(kind: String, mode: String, side: String) = viewModelScope.launch {
        busy = true; message = null
        try {
            val raw = recorder.rawSupported()
            val r = recorder.record(6, if (raw) Source.PHONE_RAW else Source.PHONE) { level = it }
            val x = r.samples.copyOfRange(r.fs / 2, r.samples.size)
            val m = Metrics.analyze(x, r.fs)
            val res = LabResult(kind, mode, side, m.noiseFloorDb, m.hissBandDb, if (raw) "phone mic (unprocessed)" else "phone mic", System.currentTimeMillis())
            labResults = labResults + res; store.saveLab(labResults)
        } catch (e: Exception) { message = e.message } finally { busy = false; level = -90f }
    }
    fun clearLab(kind: String) { labResults = labResults.filter { it.kind != kind }; store.saveLab(labResults) }

    fun saveProfile(name: String) { if (name.isBlank()) return; store.saveProfile(name.trim(), settings); profiles = store.names() }
    fun loadProfile(name: String) { store.loadProfile(name)?.let { update(it) } }
    fun deleteProfile(name: String) { store.deleteProfile(name); profiles = store.names() }
    fun export(name: String): String = store.exportProfile(name, store.loadProfile(name) ?: settings)
    fun import(text: String): Boolean {
        val r = store.importProfile(text) ?: return false
        store.saveProfile(r.first, r.second); profiles = store.names(); return true
    }
    fun resetDefaults() = applyPreset("clear")

    fun report(): String = buildString {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        appendLine("FreeBuds 6i Pro - Diagnostic Report (${df.format(Date())})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val b = bt.state.value
        appendLine("Bluetooth: ${if (b.adapterOn) "on" else "off"}, permission=${b.permission}, SCO connected=${b.scoConnected}, SCO off-call available=${b.scoAvailableOffCall}")
        b.devices.forEach { appendLine(" - ${it.name} HFP=${it.hfp} A2DP=${it.a2dp}") }
        appendLine("Platform effects: NoiseSuppressor=${Effects.ns()} AGC=${Effects.agc()} AEC=${Effects.aec()}")
        appendLine("Unprocessed mic source supported: ${recorder.rawSupported()}")
        appendLine("Last recording route: ${route ?: "none"}")
        origM?.let { appendLine("Last sample: RMS %.1f dB, peak %.1f dB, clips %d, floor %.1f dB, SNR~%.1f dB".format(it.rmsDb, it.peakDb, it.clipCount, it.noiseFloorDb, it.snrDb)) }
        procM?.let { appendLine("Processed:   RMS %.1f dB, peak %.1f dB, clips %d, floor %.1f dB, SNR~%.1f dB".format(it.rmsDb, it.peakDb, it.clipCount, it.noiseFloorDb, it.snrDb)) }
        appendLine("Lab results (dBFS, phone-mic, relative only):")
        labResults.forEach { appendLine(" ${it.kind}/${it.mode}/${it.side}: floor %.1f, hiss-band %.1f [${it.source}]".format(it.noiseFloorDb, it.hissDb)) }
        appendLine("Not supported: cellular/WhatsApp mic processing, ANC/Transparency internal control, saving settings in earbuds.")
    }

    override fun onCleared() { bt.stop() }
}
