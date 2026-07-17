package com.corall.agrotrack.data.remote.api

import com.corall.agrotrack.data.remote.dto.AlertDto
import com.corall.agrotrack.data.remote.dto.GatewayReportDto
import com.corall.agrotrack.data.remote.dto.GeneralReportDto
import com.corall.agrotrack.data.remote.dto.SensorReadingDto
import com.corall.agrotrack.data.remote.dto.ThresholdItemDto
import com.corall.agrotrack.data.remote.dto.ThresholdUpsertDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TelemetryApiService {

    @GET("telemetry/latest/{gatewayId}")
    suspend fun getLatestReadings(
        @Path("gatewayId") gatewayId: Int,
    ): Response<List<SensorReadingDto>>

    @GET("telemetry/sensor/{sensorId}/last")
    suspend fun getLastReading(
        @Path("sensorId") sensorId: Int,
    ): Response<SensorReadingDto>

    @GET("alerts")
    suspend fun getActiveAlerts(
        @Query("gateway_id") gatewayId: Int,
        @Query("resolved") resolved: Boolean = false,
    ): Response<List<AlertDto>>

    @PUT("alerts/{id}/resolve")
    suspend fun resolveAlert(
        @Path("id") alertId: Long,
    ): Response<Unit>

    @DELETE("alerts/clear")
    suspend fun clearAllAlerts(): Response<Unit>

    @GET("thresholds")
    suspend fun getThresholds(
        @Query("sensor_id") sensorId: Int,
    ): Response<List<ThresholdItemDto>>

    @POST("thresholds")
    suspend fun upsertThreshold(
        @Body body: ThresholdUpsertDto,
    ): Response<ThresholdItemDto>

    @GET("reports/sensor/{sensorId}")
    suspend fun getReportHistory(
        @Path("sensorId")  sensorId: Int,
        @Query("from")     from:     String? = null,
        @Query("to")       to:       String? = null,
        @Query("limit")    limit:    Int?    = null,
    ): Response<List<SensorReadingDto>>

    @GET("reports/alerts/{gatewayId}")
    suspend fun getAlertHistory(
        @Path("gatewayId") gatewayId: Int,
        @Query("from") from: String? = null,
        @Query("to")   to:   String? = null,
    ): Response<List<AlertDto>>

    @GET("reports/gateway/{gatewayId}")
    suspend fun getGatewayReport(
        @Path("gatewayId") gatewayId: Int,
        @Query("from") from: String,
        @Query("to")   to:   String,
    ): Response<GatewayReportDto>

    @GET("reports/general")
    suspend fun getGeneralReport(
        @Query("from") from: String,
        @Query("to")   to:   String,
    ): Response<GeneralReportDto>
}