package com.corall.agrotrack.domain.repository

import com.corall.agrotrack.domain.model.Gateway
import com.corall.agrotrack.domain.model.Sensor

interface GatewayRepository {
    suspend fun getGateways(): Result<List<Gateway>>
    suspend fun getGatewayById(gatewayId: Int): Result<Gateway?>
    suspend fun getSensorsByGateway(gatewayId: Int): Result<List<Sensor>>
    suspend fun getSensorById(sensorId: Int): Result<Sensor>
    suspend fun updateGatewayWifi(gatewayId: Int, ssid: String, password: String?, security: String): Result<Unit>
}