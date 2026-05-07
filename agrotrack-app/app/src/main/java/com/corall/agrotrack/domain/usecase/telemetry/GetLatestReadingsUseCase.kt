package com.corall.agrotrack.domain.usecase.telemetry

import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.domain.repository.TelemetryRepository
import javax.inject.Inject

class GetLatestReadingsUseCase @Inject constructor(
    private val telemetryRepository: TelemetryRepository,
) {
    suspend operator fun invoke(gatewayId: Int): Result<List<SensorReading>> =
        telemetryRepository.getLatestReadings(gatewayId)
}
