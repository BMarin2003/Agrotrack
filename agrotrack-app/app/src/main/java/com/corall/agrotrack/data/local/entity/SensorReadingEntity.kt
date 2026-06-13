package com.corall.agrotrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_readings")
data class SensorReadingEntity(
    @PrimaryKey val id: Long,
    val sensorId: Int,
    val gatewayId: Int,
    val sensorName: String,
    val unit: String,
    val temperature: Double?,
    val voltage: Double?,
    val battery: Double?,
    val receivedAt: Long,
    val status: String,
)