package com.corall.agrotrack.domain.usecase.telemetry

import com.corall.agrotrack.core.network.WebSocketManager
import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.domain.model.SensorStatus
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

sealed class LiveEvent {
    data class Reading(val data: SensorReading) : LiveEvent()
    data class NewAlert(val data: Alert) : LiveEvent()
}

class ObserveLiveReadingsUseCase @Inject constructor(
    private val wsManager: WebSocketManager,
    private val gson: Gson,
) {
    operator fun invoke(): Flow<LiveEvent> =
        wsManager.messages
            .map { raw -> parseEvent(raw) }
            .filterNotNull()

    private fun parseEvent(raw: String): LiveEvent? {
        return try {
            val obj  = gson.fromJson(raw, JsonObject::class.java)
            val type = obj.get("type")?.asString ?: return null
            val data = obj.getAsJsonObject("data") ?: return null

            when (type) {
                "reading" -> LiveEvent.Reading(
                    SensorReading(
                        sensorId    = data.get("sensor_id").asInt,
                        gatewayId   = data.get("gateway_id").asInt,
                        temperature = data.get("temperature")?.takeIf { !it.isJsonNull }?.asDouble,
                        voltage     = data.get("voltage")?.takeIf { !it.isJsonNull }?.asDouble,
                        battery     = data.get("battery")?.takeIf { !it.isJsonNull }?.asDouble,
                        receivedAt  = System.currentTimeMillis(),
                    )
                )
                "alert" -> LiveEvent.NewAlert(
                    Alert(
                        id        = data.get("id")?.asLong ?: 0L,
                        sensorId  = data.get("sensor_id").asInt,
                        gatewayId = data.get("gateway_id").asInt,
                        type      = data.get("type")?.asString ?: "unknown",
                        metric    = data.get("metric")?.takeIf { !it.isJsonNull }?.asString,
                        value     = data.get("value")?.takeIf { !it.isJsonNull }?.asDouble,
                        threshold = data.get("threshold")?.takeIf { !it.isJsonNull }?.asDouble,
                        message   = data.get("message")?.asString ?: "",
                        resolved  = false,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                else -> null
            }
        } catch (e: Exception) { null }
    }
}
