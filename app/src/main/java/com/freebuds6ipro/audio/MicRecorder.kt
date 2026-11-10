package com.freebuds6ipro.audio

import android.annotation.SuppressLint
import android.content.*
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.freebuds6ipro.dsp.Metrics
import kotlinx.coroutines.*
import kotlin.math.max

enum class Source { SCO, PHONE, PHONE_RAW }
class RecResult(val samples: FloatArray, val fs: Int, val route: String, val scoStarted: Boolean)

object Effects {
    fun ns() = NoiseSuppressor.isAvailable()
    fun agc() = AutomaticGainControl.isAvailable()
    fun aec() = AcousticEchoCanceler.isAvailable()
}

class MicRecorder(private val ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun rawSupported() = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    private suspend fun startSco(): Boolean {
        val d = CompletableDeferred<Boolean>()
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == AudioManager.SCO_AUDIO_STATE_CONNECTED) d.complete(true)
            }
        }
        ContextCompat.registerReceiver(ctx, rx, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED), ContextCompat.RECEIVER_EXPORTED)
        @Suppress("DEPRECATION") am.startBluetoothSco()
        val ok = withTimeoutOrNull(4000) { d.await() } == true
        runCatching { ctx.unregisterReceiver(rx) }
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun record(seconds: Int, source: Source, onLevel: (Float) -> Unit): RecResult = withContext(Dispatchers.IO) {
        var scoOk = false
        if (source == Source.SCO) { am.mode = AudioManager.MODE_IN_COMMUNICATION; scoOk = startSco() }
        val fs = if (source == Source.SCO) 16000 else 44100
        val src = when (source) {
            Source.SCO -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            Source.PHONE -> MediaRecorder.AudioSource.MIC
            Source.PHONE_RAW -> MediaRecorder.AudioSource.UNPROCESSED
        }
        val min = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(src, fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, fs))
        try {
            check(rec.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
            rec.startRecording()
            val route = describe(rec.routedDevice)
            val total = fs * seconds; val out = FloatArray(total); val buf = ShortArray(1024); var n = 0
            while (n < total && isActive) {
                val r = rec.read(buf, 0, minOf(buf.size, total - n))
                if (r <= 0) break
                val chunk = FloatArray(r) { buf[it] / 32768f }
                chunk.copyInto(out, n); n += r
                onLevel(Metrics.rmsDb(chunk))
            }
            RecResult(out.copyOf(n), fs, route, scoOk)
        } finally {
            runCatching { rec.stop() }; rec.release()
            if (source == Source.SCO) { @Suppress("DEPRECATION") am.stopBluetoothSco(); am.mode = AudioManager.MODE_NORMAL }
        }
    }

    private fun describe(d: AudioDeviceInfo?): String = when (d?.type) {
        null -> "unknown"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO (${d.productName})"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone mic"
        else -> "type ${d.type} (${d.productName})"
    }
}

object Player {
    suspend fun play(x: FloatArray, fs: Int) = withContext(Dispatchers.IO) {
        val s = ShortArray(x.size) { (x[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(fs).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(s.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        try { t.write(s, 0, s.size); t.play(); delay(x.size * 1000L / fs + 300) } finally { runCatching { t.stop() }; t.release() }
    }
}
