package com.corall.agrotrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: Long,
    val sensorId:  Int,
    val gatewayId: Int,
    val type:      String,
    val metric:    String?,
    val value:     Double?,
    val threshold: Double?,
    val message:   String,
    val resolved:  Boolean,
    val createdAt: Long,
)
