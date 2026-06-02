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

/** Wejścia nagrywania (mono — jedno [AudioDeviceInfo] na sesję), filtrowane whitelistą typów. */
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
                infos
                    .filter { isWhitelistedInputType(it.type) }
                    .map { InputDeviceOption(it, labelFor(it)) }
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
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headset mic"
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                -> "USB mic"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
                else ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        info.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    ) {
                        "BLE headset"
                    } else {
                        "Input"
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

        /** Typy wejść pokazywane i dostępne do nagrywania. */
        fun isWhitelistedInputType(type: Int): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                type == AudioDeviceInfo.TYPE_BLE_HEADSET
            ) {
                return true
            }
            return when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                -> true
                else -> false
            }
        }

        fun isUsbInputFamily(type: Int): Boolean =
            type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                type == AudioDeviceInfo.TYPE_USB_ACCESSORY

        /**
         * Domyślny wybór: USB (dowolna rodzina) → przewodowy headset → pierwszy wbudowany mikrofon
         * → pierwszy z whitelistowanej listy.
         */
        fun defaultInputDeviceId(options: List<InputDeviceOption>): Int? {
            if (options.isEmpty()) return null
            options.firstOrNull { isUsbInputFamily(it.info.type) }?.let {
                return it.info.id
            }
            options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }?.let {
                return it.info.id
            }
            options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }?.let {
                return it.info.id
            }
            return options.first().info.id
        }

        fun firstBuiltinMicId(options: List<InputDeviceOption>): Int? =
            options.firstOrNull { it.info.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }?.info?.id

        fun listHasUsbFamily(options: List<InputDeviceOption>): Boolean =
            options.any { isUsbInputFamily(it.info.type) }
    }
}
