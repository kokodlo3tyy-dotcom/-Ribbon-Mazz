package com.ribbonmidi.app

import android.content.Context
import android.content.SharedPreferences

enum class MaqamPreset(val displayName: String, val quarterToneIntervals: List<Int>) {
    BAYATI("Bayati", listOf(2)),          // الدرجة الثانية مشرّقة
    RAST("Rast", listOf(4, 11)),          // الثالثة والسابعة مشرّقة
    SABA("Saba", listOf(2)),              // الدرجة الثانية مشرّقة
    SIKAH("Sikah", listOf(0, 7)),         // الأولى والخامسة مشرّقة
    HIJAZ("Hijaz", emptyList())           // بدون ربع تون
}

class ScaleManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("arabic_ribbon_banks", Context.MODE_PRIVATE)

    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // مصفوفة الـ 12 نوتة: true معناها النوتة مشرّقة (-50 سنت)
    fun getMaqamNotes(preset: MaqamPreset, rootKey: Int): BooleanArray {
        val result = BooleanArray(12) { false }
        for (interval in preset.quarterToneIntervals) {
            val noteIndex = (rootKey + interval) % 12
            result[noteIndex] = true
        }
        return result
    }

    fun saveBank(bankIndex: Int, notes: BooleanArray, transpose: Int) {
        val notesStr = notes.joinToString(",") { if (it) "1" else "0" }
        prefs.edit()
            .putString("bank_${bankIndex}_notes", notesStr)
            .putInt("bank_${bankIndex}_transpose", transpose)
            .putBoolean("bank_${bankIndex}_saved", true)
            .apply()
    }

    fun loadBank(bankIndex: Int): Pair<BooleanArray, Int>? {
        if (!prefs.getBoolean("bank_${bankIndex}_saved", false)) return null
        val notesStr = prefs.getString("bank_${bankIndex}_notes", null) ?: return null
        val parts = notesStr.split(",")
        if (parts.size != 12) return null
        val notes = BooleanArray(12) { parts[it] == "1" }
        val transpose = prefs.getInt("bank_${bankIndex}_transpose", 0)
        return Pair(notes, transpose)
    }

    fun isBankSaved(bankIndex: Int): Boolean {
        return prefs.getBoolean("bank_${bankIndex}_saved", false)
    }
}
