package com.freebuds6ipro.audio

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

data class BtDevice(val name: String, val hfp: Boolean, val a2dp: Boolean) {
    val isFreeBuds get() = name.contains("FreeBuds", ignoreCase = true)
}
data class BtState(val adapterPresent: Boolean = true, val adapterOn: Boolean = false,
                   val permission: Boolean = false, val devices: List<BtDevice> = emptyList(),
                   val scoConnected: Boolean = false, val scoAvailableOffCall: Boolean = false)

/** Devices are keyed internally by address only for de-duplication; addresses are never stored or shown. */
@SuppressLint("MissingPermission")
class BtManager(private val ctx: Context) {
    private val adapter: BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val state = MutableStateFlow(BtState())
    private var hs: BluetoothHeadset? = null
    private var a2: BluetoothA2dp? = null
    private var registered = false
    private var sco = false

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                sco = i.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == AudioManager.SCO_AUDIO_STATE_CONNECTED
            refresh()
        }
    }
    private val proxy = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) hs = p as BluetoothHeadset else a2 = p as BluetoothA2dp
            refresh()
        }
        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) hs = null else a2 = null
            refresh()
        }
    }

    private fun hasPerm() = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun start() {
        refresh()
        if (!hasPerm() || adapter == null || registered) return
        registered = true
        adapter.getProfileProxy(ctx, proxy, BluetoothProfile.HEADSET)
        adapter.getProfileProxy(ctx, proxy, BluetoothProfile.A2DP)
        val f = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        ContextCompat.registerReceiver(ctx, rx, f, ContextCompat.RECEIVER_EXPORTED)
    }

    fun stop() {
        if (registered) { runCatching { ctx.unregisterReceiver(rx) }; registered = false }
        adapter?.closeProfileProxy(BluetoothProfile.HEADSET, hs); adapter?.closeProfileProxy(BluetoothProfile.A2DP, a2)
        hs = null; a2 = null
    }

    fun refresh() {
        val perm = hasPerm()
        val m = linkedMapOf<String, BtDevice>()
        if (perm) {
            hs?.connectedDevices?.forEach { m[it.address] = BtDevice(it.name ?: "?", true, m[it.address]?.a2dp == true) }
            a2?.connectedDevices?.forEach { val o = m[it.address]; m[it.address] = BtDevice(it.name ?: "?", o?.hfp == true, true) }
        }
        state.value = BtState(adapter != null, adapter?.isEnabled == true, perm, m.values.toList(), sco, am.isBluetoothScoAvailableOffCall)
    }
}
