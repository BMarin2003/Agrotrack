package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MaintenanceRecordDto(
    @SerializedName("id")             val id:            Int,
    @SerializedName("gateway_id")     val gatewayId:     Int,
    @SerializedName("notes")          val notes:         String?,
    @SerializedName("performed_at")   val performedAt:   String,
    @SerializedName("next_scheduled") val nextScheduled: String?,
)

data class MaintenanceSaveDto(
    @SerializedName("notes")          val notes:         String? = null,
    @SerializedName("next_scheduled") val nextScheduled: String? = null,
)
