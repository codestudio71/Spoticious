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

data class InputDeviceOption(
    val info: AudioDeviceInfo,
    val label: String,
)

/** Lista wejść audio dostępnych dla nagrywania (MIC / headset itd.). */
class RecordingDeviceRepository(context: Context) {

    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<InputDeviceOption>>(emptyList())
    val devices: StateFlow<List<InputDeviceOption>> = _devices.asStateFlow()

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
                    audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                } else {
                    emptyArray()
                }
            _devices.value =
                infos.map { InputDeviceOption(it, labelFor(it)) }
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
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Mic"
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                -> "Headset mic"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
                AudioDeviceInfo.TYPE_TELEPHONY -> "Phone"
                else -> "Input"
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
}
