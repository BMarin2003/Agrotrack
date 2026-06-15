package com.corall.agrotrack.data.remote.api

import com.corall.agrotrack.data.remote.dto.GatewayDto
import com.corall.agrotrack.data.remote.dto.SensorDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SensorsApiService {

    @GET("sensors/gateways")
    suspend fun getGateways(): Response<List<GatewayDto>>

    @GET("sensors")
    suspend fun getSensorsByGateway(
        @Query("gateway_id") gatewayId: Int,
    ): Response<List<SensorDto>>
}