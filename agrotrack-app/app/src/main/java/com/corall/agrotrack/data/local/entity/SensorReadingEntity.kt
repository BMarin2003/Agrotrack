package com.corall.agrotrack.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sensor_readings",
    indices   = [Index("sensorId"), Index("receivedAt")]
)
data class SensorReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sensorId:    Int,
    val gatewayId:   Int,
    val sensorName:  String,
    val unit:        String,
    val temperature: Double?,
    val voltage:     Double?,
    val battery:     Double?,
    val receivedAt:  Long,
)
