package com.wmm.ledger.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

internal data class PaymentCaptureOptions(val enabled: Boolean = false, val sources: Set<String> = PaymentSource.entries.map { it.name }.toSet(),
    val consent: Boolean = false) {
    fun allows(source: PaymentSource) = enabled && consent && source.name in sources
}

internal class PaymentCaptureSettings(context: Context) {
    private val preferences = context.getSharedPreferences("payment_capture_settings", Context.MODE_PRIVATE)
    fun snapshot() = PaymentCaptureOptions(preferences.getBoolean("enabled", false),
        preferences.getStringSet("sources", PaymentSource.entries.map { it.name }.toSet()).orEmpty().toSet(),
        preferences.getBoolean("consent_v1", false))

    fun setEnabled(enabled: Boolean, consent: Boolean = false) {
        val editor = preferences.edit().putBoolean("enabled", enabled && snapshot().sources.isNotEmpty())
        if (consent) editor.putBoolean("consent_v1", true)
        editor.apply()
    }

    fun setSource(source: PaymentSource, enabled: Boolean) {
        val selected = snapshot().sources.toMutableSet().apply { if (enabled) add(source.name) else remove(source.name) }
        preferences.edit().putStringSet("sources", selected).apply {
            if (selected.isEmpty()) putBoolean("enabled", false)
        }.apply()
    }

    fun observe() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(snapshot())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
