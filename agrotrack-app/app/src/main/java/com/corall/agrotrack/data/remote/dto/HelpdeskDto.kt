package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TicketDto(
    @SerializedName("id")               val id:            Long,
    @SerializedName("gateway_id")       val gatewayId:      Int?,
    @SerializedName("gateway_name")     val gatewayName:    String?,
    @SerializedName("created_by")       val createdBy:      Int,
    @SerializedName("created_by_name")  val createdByName:  String?,
    @SerializedName("subject")          val subject:        String,
    @SerializedName("description")      val description:    String?,
    @SerializedName("status")           val status:         String,
    @SerializedName("created_at")       val createdAt:      String,
    @SerializedName("updated_at")       val updatedAt:      String,
)

data class TicketCreateDto(
    @SerializedName("gateway_id")  val gatewayId:   Int? = null,
    @SerializedName("subject")     val subject:     String,
    @SerializedName("description") val description: String? = null,
)

data class TicketStatusUpdateDto(
    @SerializedName("status") val status: String,
)
