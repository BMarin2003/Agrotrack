package com.corall.agrotrack.data.remote.api

import com.corall.agrotrack.data.remote.dto.AlertDto
import com.corall.agrotrack.data.remote.dto.SensorReadingDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TelemetryApiService {
    @GET("telemetry/latest/{gatewayId}")
    suspend fun getLatestReadings(@Path("gatewayId") gatewayId: Int): Response<List<SensorReadingDto>>

    @GET("alerts")
    suspend fun getActiveAlerts(
        @Query("gateway_id") gatewayId: Int,
        @Query("resolved")   resolved: Boolean = false,
    ): Response<List<AlertDto>>

    @PUT("alerts/{id}/resolve")
    suspend fun resolveAlert(@Path("id") alertId: Long): Response<Unit>
}
