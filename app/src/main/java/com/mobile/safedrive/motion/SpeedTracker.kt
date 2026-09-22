package com.mobile.safedrive.motion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mobile.safedrive.session.RoutePoint

/** GPS speed, distance and a downsampled route from FusedLocationProvider (spec §2.2). */
class SpeedTracker(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var lastFix: Location? = null
    private var lastRouteFix: Location? = null
    private var distanceMeters = 0f
    private val route = ArrayList<RoutePoint>(MAX_ROUTE_POINTS)
    private var running = false

    var onUpdate: ((speedKmh: Float, distanceKm: Float) -> Unit)? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onFix)
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(intervalMs: Long) {
        if (running || !hasPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        running = true
    }

    fun stop() {
        if (!running) return
        client.removeLocationUpdates(callback)
        running = false
    }

    fun resetTrip() {
        lastFix = null
        lastRouteFix = null
        distanceMeters = 0f
        route.clear()
    }

    fun routeSnapshot(): List<RoutePoint> = route.toList()

    val distanceKm: Float get() = distanceMeters / METERS_PER_KM

    private fun onFix(location: Location) {
        val speedKmh = if (location.hasSpeed()) location.speed * MS_TO_KMH else 0f
        if (location.accuracy <= MAX_ACCURACY_M) {
            lastFix?.let { distanceMeters += it.distanceTo(location) }
            lastFix = location
            val previous = lastRouteFix
            if (previous == null || previous.distanceTo(location) >= ROUTE_SPACING_M) {
                addRoutePoint(location)
                lastRouteFix = location
            }
        }
        onUpdate?.invoke(speedKmh, distanceKm)
    }

    private fun addRoutePoint(location: Location) {
        if (route.size >= MAX_ROUTE_POINTS) {
            // Keep the whole trip's shape: halve resolution instead of dropping the start.
            val kept = route.filterIndexed { index, _ -> index % 2 == 0 }
            route.clear()
            route.addAll(kept)
        }
        route += RoutePoint(location.latitude, location.longitude)
    }

    private companion object {
        const val MS_TO_KMH = 3.6f
        const val METERS_PER_KM = 1000f
        const val MAX_ACCURACY_M = 50f
        const val ROUTE_SPACING_M = 50f
        const val MAX_ROUTE_POINTS = 400
    }
}
