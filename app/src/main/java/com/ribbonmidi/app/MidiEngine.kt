package com.ribbonmidi.app

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

class MidiEngine(
    context: Context,
    private val onConnectionChanged: (Boolean, String) -> Unit
) {
    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val handler = Handler(Looper.getMainLooper())

    private var currentDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null   // بنبعت عليه للكمبيوتر
    private var outputPort: MidiOutputPort? = null // بنستقبل منه من الكمبيوتر (MIDI Thru)

    var pitchBendRangeSemitones: Int = 2
    var quarterToneCents: Float = -50f
    var transposeSemitones: Int = 0
    var quarterToneMask: BooleanArray = BooleanArray(12) { false }

    private var currentRibbonValue: Float = 0f     // من -1.0 إلى +1.0
    private var currentNoteQuarterOffset: Float = 0f

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            autoConnect()
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            if (currentDevice?.info?.id == device.id) {
                disconnect()
                autoConnect()
            }
        }
    }

    fun start() {
        midiManager?.registerDeviceCallback(deviceCallback, handler)
        autoConnect()
    }

    fun stop() {
        midiManager?.unregisterDeviceCallback(deviceCallback)
        disconnect()
    }

    fun autoConnect() {
        val manager = midiManager ?: return
        val devices = manager.devices
        if (devices.isEmpty()) {
            onConnectionChanged(false, "No USB MIDI")
            return
        }
        // بنختار أول جهاز USB MIDI متاح (غالباً Android USB Peripheral Port)
        val target = devices.firstOrNull { it.inputPortCount > 0 } ?: devices.first()
        connectToDevice(target)
    }

    private fun connectToDevice(info: MidiDeviceInfo) {
        disconnect()
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "USB MIDI"
        midiManager?.openDevice(info, { device ->
            if (device == null) {
                onConnectionChanged(false, "Connection Failed")
                return@openDevice
            }
            currentDevice = device
            if (info.inputPortCount > 0) {
                inputPort = device.openInputPort(0)
            }
            if (info.outputPortCount > 0) {
                outputPort = device.openOutputPort(0)
                outputPort?.connect(thruReceiver)
            }
            onConnectionChanged(true, name)
        }, handler)
    }

    private fun disconnect() {
        try {
            outputPort?.disconnect(thruReceiver)
            outputPort?.close()
            inputPort?.close()
            currentDevice?.close()
        } catch (_: Exception) {}
        outputPort = null
        inputPort = null
        currentDevice = null
        onConnectionChanged(false, "Disconnected")
    }

    // مستقبل التمرير الذكي (MIDI Thru): يستقبل النوتة، يطبق الترانسبوز والربع تون، ويبعتها تاني
    private val thruReceiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            if (count < 1) return
            val status = msg[offset].toInt() and 0xFF
            val command = status and 0xF0
            val channel = status and 0x0F

            if ((command == 0x90 || command == 0x80) && count >= 3) {
                val rawNote = msg[offset + 1].toInt() and 0x7F
                val velocity = msg[offset + 2].toInt() and 0x7F
                val transposedNote = (rawNote + transposeSemitones).coerceIn(0, 127)

                val isNoteOn = (command == 0x90 && velocity > 0)
                if (isNoteOn) {
                    val noteInOctave = transposedNote % 12
                    currentNoteQuarterOffset = if (quarterToneMask[noteInOctave]) {
                        quarterToneCents / (pitchBendRangeSemitones * 100f)
                    } else {
                        0f
                    }
                    sendCombinedPitchBend(channel)
                }

                sendRaw(byteArrayOf(status.toByte(), transposedNote.toByte(), velocity.toByte()))
            } else {
                // تمرير باقي رسائل الـ MIDI كما هي
                val copy = msg.copyOfRange(offset, offset + count)
                sendRaw(copy)
            }
        }
    }

    fun updateRibbonPitch(normalizedValue: Float) {
        currentRibbonValue = normalizedValue.coerceIn(-1f, 1f)
        sendCombinedPitchBend(0)
    }

    private fun sendCombinedPitchBend(channel: Int) {
        val totalNormalized = (currentRibbonValue + currentNoteQuarterOffset).coerceIn(-1f, 1f)
        // دقة 14-bit: المنتصف 8192، المدى من 0 إلى 16383
        val bend14Bit = (8192 + (totalNormalized * 8191f)).roundToInt().coerceIn(0, 16383)
        val lsb = (bend14Bit and 0x7F).toByte()
        val msb = ((bend14Bit shr 7) and 0x7F).toByte()
        val status = (0xE0 or (channel and 0x0F)).toByte()
        sendRaw(byteArrayOf(status, lsb, msb))
    }

    fun sendCC(ccNumber: Int, value: Int, channel: Int = 0) {
        val status = (0xB0 or (channel and 0x0F)).toByte()
        sendRaw(byteArrayOf(status, (ccNumber and 0x7F).toByte(), (value.coerceIn(0, 127)).toByte()))
    }

    fun sendMonoPoly(isMono: Boolean) {
        if (isMono) {
            sendCC(126, 1) // Mono Mode On
        } else {
            sendCC(127, 0) // Poly Mode On
        }
    }

    fun sendLegato(enabled: Boolean) {
        sendCC(68, if (enabled) 127 else 0)
    }

    fun sendGlideEnable(enabled: Boolean) {
        sendCC(65, if (enabled) 127 else 0)
    }

    fun sendGlideTime(value: Int) {
        sendCC(5, value)
    }

    // إرسال أمر RPN 00 00 القياسي لتغيير رينج البيتش في الآلات الداعمة
    fun sendPitchBendRangeRpn(semitones: Int, channel: Int = 0) {
        pitchBendRangeSemitones = semitones
        sendCC(101, 0, channel)
        sendCC(100, 0, channel)
        sendCC(6, semitones.coerceIn(1, 24), channel)
        sendCC(38, 0, channel)
        sendCC(101, 127, channel)
        sendCC(100, 127, channel)
    }

    private fun sendRaw(bytes: ByteArray) {
        try {
            inputPort?.send(bytes, 0, bytes.size)
        } catch (_: Exception) {}
    }
}
