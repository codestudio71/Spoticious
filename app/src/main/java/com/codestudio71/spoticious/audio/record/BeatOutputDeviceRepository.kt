package com.codestudio71.spoticious.audio.record

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OutputDeviceOption(
    val info: AudioDeviceInfo,
    val label: String,
)

/** Wyjścia audio dla podglądu bitu (ExoPlayer), filtrowane whitelistą typów. */
class BeatOutputDeviceRepository(context: Context) {

    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<OutputDeviceOption>>(emptyList())
    val devices: StateFlow<List<OutputDeviceOption>> = _devices.asStateFlow()

    private val callback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                refresh()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                refresh()
            }
        }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, mainHandler)
        refresh()
    }

    fun stop() {
        try {
            audioManager.unregisterAudioDeviceCallback(callback)
        } catch (_: Exception) {
        }
    }

    private fun refresh() {
        try {
            val infos =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                } else {
                    emptyArray()
                }
            _devices.value =
                infos
                    .filter { isWhitelistedOutputType(it.type) }
                    .map { OutputDeviceOption(it, labelFor(it)) }
        } catch (_: Exception) {
            _devices.value = emptyList()
        }
    }

    private fun labelFor(info: AudioDeviceInfo): String {
        val prod =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.productName?.toString()?.trim().orEmpty()
            } else {
                ""
            }
        val typeLabel =
            when (info.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                else ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        info.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    ) {
                        "BLE headset"
                    } else {
                        "Output"
                    }
            }
        return buildString {
            if (prod.isNotEmpty()) {
                append(prod)
                append(" · ")
            }
            append(typeLabel)
            append(" (")
            append(info.id)
            append(")")
        }
    }

    companion object {

        fun isWhitelistedOutputType(type: Int): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                type == AudioDeviceInfo.TYPE_BLE_HEADSET
            ) {
                return true
            }
            return when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                -> true
                else -> false
            }
        }

        /** Domyślnie słuchawki / headset, potem USB, BT, na końcu głośnik. */
        fun defaultOutputDevice(options: List<OutputDeviceOption>): AudioDeviceInfo? {
            if (options.isEmpty()) return null
            options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }?.let {
                return it.info
            }
            options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }?.let {
                return it.info
            }
            options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_USB_HEADSET }?.let {
                return it.info
            }
            options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_USB_DEVICE }?.let {
                return it.info
            }
            options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }?.let {
                return it.info
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_BLE_HEADSET }?.let {
                    return it.info
                }
            }
            options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let {
                return it.info
            }
            return options.first().info
        }
    }
}
