package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SensorAliasDto(
    @SerializedName("alias") val alias: String?,
)

data class SensorAliasSaveDto(
    @SerializedName("alias") val alias: String,
)
