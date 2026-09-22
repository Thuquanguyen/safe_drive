package com.mobile.safedrive.session

import android.content.Context
import com.mobile.safedrive.drowsiness.DrowsinessState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local, encrypted trip history. Newest session first. */
class SessionRepository(context: Context) {

    private val file = File(context.filesDir, "sessions.bin")
    private val mutex = Mutex()
    private val _sessions = MutableStateFlow<List<DrivingSession>>(emptyList())
    val sessions: StateFlow<List<DrivingSession>> = _sessions.asStateFlow()
    private var loaded = false

    suspend fun load() = mutex.withLock { ensureLoaded() }

    suspend fun find(id: String): DrivingSession? {
        load()
        return _sessions.value.firstOrNull { it.sessionId == id }
    }

    suspend fun save(session: DrivingSession) = mutex.withLock {
        ensureLoaded()
        val updated = listOf(session) + _sessions.value.filterNot { it.sessionId == session.sessionId }
        _sessions.value = updated
        withContext(Dispatchers.IO) {
            val json = JSONArray().apply { updated.forEach { put(it.toJson()) } }.toString()
            file.writeBytes(CryptoBox.encrypt(json.toByteArray()))
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _sessions.value = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching {
                val array = JSONArray(String(CryptoBox.decrypt(file.readBytes())))
                List(array.length()) { array.getJSONObject(it).toSession() }
            }.getOrDefault(emptyList())
        }
        loaded = true
    }

    private fun DrivingSession.toJson() = JSONObject().apply {
        put("id", sessionId)
        put("start", startTime)
        put("end", endTime)
        put("km", distanceKm.toDouble())
        put("max", maxDrowsinessLevel.name)
        put("att", attentionAlerts)
        put("drw", drowsyAlerts)
        put("crt", criticalAlerts)
        put("auto", autoStarted)
        put("route", JSONArray().apply {
            route.forEach { put(JSONArray().put(it.lat).put(it.lng)) }
        })
    }

    private fun JSONObject.toSession(): DrivingSession {
        val routeJson = optJSONArray("route") ?: JSONArray()
        return DrivingSession(
            sessionId = getString("id"),
            startTime = getLong("start"),
            endTime = getLong("end"),
            distanceKm = getDouble("km").toFloat(),
            maxDrowsinessLevel = runCatching { DrowsinessState.valueOf(getString("max")) }
                .getOrDefault(DrowsinessState.NORMAL),
            attentionAlerts = getInt("att"),
            drowsyAlerts = getInt("drw"),
            criticalAlerts = getInt("crt"),
            autoStarted = optBoolean("auto"),
            route = List(routeJson.length()) {
                val p = routeJson.getJSONArray(it)
                RoutePoint(p.getDouble(0), p.getDouble(1))
            },
        )
    }
}
