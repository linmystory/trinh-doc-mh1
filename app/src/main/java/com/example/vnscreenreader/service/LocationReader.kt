package com.example.vnscreenreader.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.function.Consumer

/**
 * Lấy vị trí hiện tại MỘT LẦN theo yêu cầu (không theo dõi liên tục, không lưu, không gửi đi): thử GPS trước (chính xác nhất),
 * rồi nhà cung cấp hợp nhất/mạng, cuối cùng là vị trí cũ nhất còn nhớ. Đổi tọa độ thành địa chỉ bằng [Geocoder] của hệ thống
 * (dịch vụ vị trí của điện thoại, ứng dụng này không có quyền INTERNET). Mọi kết quả trả về trên luồng chính.
 */
class LocationReader(private val context: Context) {
    class Fix(val latitude: Double, val longitude: Double, val accuracyMeters: Float?, val ageMs: Long, val address: String?)

    private val main = Handler(Looper.getMainLooper())
    private var signal: CancellationSignal? = null
    private var generation = 0

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Hủy lần đang chạy (nếu có). */
    fun cancel() {
        generation++
        signal?.cancel()
        signal = null
        main.removeCallbacksAndMessages(null)
    }

    /** Gọi từ luồng chính. [onResult] nhận null nếu không lấy được vị trí. */
    fun locate(onResult: (Fix?) -> Unit) {
        cancel()
        val gen = generation
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || !hasPermission()) {
            onResult(null)
            return
        }
        val providers = candidateProviders(manager)
        tryProviders(manager, providers, 0, gen) { location ->
            if (gen != generation) return@tryProviders
            val best = location ?: lastKnown(manager, providers)
            if (best == null) {
                onResult(null)
            } else {
                geocode(best, gen, onResult)
            }
        }
    }

    private fun candidateProviders(manager: LocationManager): List<String> {
        val wanted = listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return wanted.filter { name ->
            try {
                manager.getProvider(name) != null && manager.isProviderEnabled(name)
            } catch (e: RuntimeException) {
                false
            }
        }
    }

    private fun tryProviders(manager: LocationManager, providers: List<String>, index: Int, gen: Int, done: (Location?) -> Unit) {
        if (gen != generation) return
        if (index >= providers.size) {
            done(null)
            return
        }
        val provider = providers[index]
        val cancel = CancellationSignal()
        signal = cancel
        var finished = false
        val finish = { location: Location? ->
            if (!finished && gen == generation) {
                finished = true
                main.removeCallbacksAndMessages(null)
                if (location != null) done(location) else tryProviders(manager, providers, index + 1, gen, done)
            }
        }
        // GPS cần thời gian để dò vệ tinh; các nhà cung cấp khác nhanh hơn.
        val timeout = if (provider == LocationManager.GPS_PROVIDER) GPS_TIMEOUT_MS else OTHER_TIMEOUT_MS
        main.postDelayed({
            cancel.cancel()
            finish(null)
        }, timeout)
        try {
            manager.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context), Consumer { location -> finish(location) })
        } catch (e: SecurityException) {
            Log.w(TAG, "Không có quyền vị trí", e)
            finish(null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Lỗi khi lấy vị trí từ $provider", e)
            finish(null)
        }
    }

    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? = try {
        providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
    } catch (e: SecurityException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private fun geocode(location: Location, gen: Int, onResult: (Fix?) -> Unit) {
        val ageMs = ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        var delivered = false
        val deliver = { address: String? ->
            if (!delivered && gen == generation) {
                delivered = true
                main.removeCallbacksAndMessages(null)
                onResult(Fix(location.latitude, location.longitude, accuracy, ageMs, address))
            }
        }
        if (!Geocoder.isPresent()) {
            deliver(null)
            return
        }
        main.postDelayed({ deliver(null) }, GEOCODE_TIMEOUT_MS)
        try {
            Geocoder(context, context.resources.configuration.locales[0]).getFromLocation(
                location.latitude,
                location.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        val line = addresses.firstOrNull()?.getAddressLine(0)
                        main.post { deliver(line) }
                    }

                    override fun onError(errorMessage: String?) {
                        main.post { deliver(null) }
                    }
                },
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không đổi được tọa độ thành địa chỉ", e)
            deliver(null)
        }
    }

    private companion object {
        const val TAG = "LocationReader"
        const val GPS_TIMEOUT_MS = 12_000L
        const val OTHER_TIMEOUT_MS = 6_000L
        const val GEOCODE_TIMEOUT_MS = 6_000L
    }
}
