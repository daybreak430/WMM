package com.wmm.ledger.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.Bundle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.floor
import java.util.UUID

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
internal suspend fun readHabitArea(context: Context): String? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time in 0..300_000 }.maxByOrNull(Location::getTime)
    val location = cached ?: withTimeoutOrNull(3500) {
        val provider = providers.firstOrNull() ?: return@withTimeoutOrNull null
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location)
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: RuntimeException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
    // 仅记录约两公里网格的哈希，原始坐标不落盘、不上传。
    return location?.let {
        val grid = "${floor(it.latitude / .025).toInt()}:${floor(it.longitude / .025).toInt()}"
        val settings = context.getSharedPreferences("habit_location", Context.MODE_PRIVATE)
        val salt = settings.getString("salt", null) ?: UUID.randomUUID().toString().also { generated ->
            settings.edit().putString("salt", generated).apply()
        }
        sha256("$salt|$grid".encodeToByteArray()).take(24)
    }
}
