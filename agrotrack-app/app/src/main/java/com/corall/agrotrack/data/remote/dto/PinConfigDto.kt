package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PinConfigDto(
    @SerializedName("gateway_ids") val gatewayIds: List<Int>,
    @SerializedName("pin")         val pin:        String,
)
