package com.corall.agrotrack.domain.repository

import com.corall.agrotrack.domain.model.Gateway
import com.corall.agrotrack.domain.model.Sensor

interface GatewayRepository {
    suspend fun getGateways(): Result<List<Gateway>>
    suspend fun getSensorsByGateway(gatewayId: Int): Result<List<Sensor>>
}