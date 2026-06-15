package com.corall.agrotrack.domain.usecase.gateway

import com.corall.agrotrack.domain.model.Gateway
import com.corall.agrotrack.domain.repository.GatewayRepository
import javax.inject.Inject

class GetGatewaysUseCase @Inject constructor(
    private val gatewayRepository: GatewayRepository,
) {
    suspend operator fun invoke(): Result<List<Gateway>> = gatewayRepository.getGateways()
}