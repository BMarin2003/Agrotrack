package com.corall.agrotrack.data.remote.api

import com.corall.agrotrack.data.remote.dto.CalibrationDto
import com.corall.agrotrack.data.remote.dto.CalibrationSaveDto
import com.corall.agrotrack.data.remote.dto.GatewayDto
import com.corall.agrotrack.data.remote.dto.MaintenanceRecordDto
import com.corall.agrotrack.data.remote.dto.MaintenanceSaveDto
import com.corall.agrotrack.data.remote.dto.SensorDto
import com.corall.agrotrack.data.remote.dto.WifiConfigDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SensorsApiService {

    @GET("sensors/gateways")
    suspend fun getGateways(): Response<List<GatewayDto>>

    @GET("sensors")
    suspend fun getSensorsByGateway(
        @Query("gateway_id") gatewayId: Int,
    ): Response<List<SensorDto>>

    @GET("sensors")
    suspend fun getAllSensors(): Response<List<SensorDto>>

    @GET("sensors/{id}")
    suspend fun getSensorById(
        @Path("id") id: Int,
    ): Response<SensorDto>

    @PUT("sensors/gateways/{id}/wifi")
    suspend fun updateGatewayWifi(
        @Path("id") gatewayId: Int,
        @Body body: WifiConfigDto,
    ): Response<Unit>

    @GET("sensors/{id}/calibration")
    suspend fun getCalibration(@Path("id") sensorId: Int): Response<CalibrationDto?>

    @POST("sensors/{id}/calibration")
    suspend fun saveCalibration(
        @Path("id") sensorId: Int,
        @Body body: CalibrationSaveDto,
    ): Response<CalibrationDto>

    @GET("sensors/gateways/{id}/maintenance")
    suspend fun listMaintenance(@Path("id") gatewayId: Int): Response<List<MaintenanceRecordDto>>

    @POST("sensors/gateways/{id}/maintenance")
    suspend fun registerMaintenance(
        @Path("id") gatewayId: Int,
        @Body body: MaintenanceSaveDto,
    ): Response<MaintenanceRecordDto>
}