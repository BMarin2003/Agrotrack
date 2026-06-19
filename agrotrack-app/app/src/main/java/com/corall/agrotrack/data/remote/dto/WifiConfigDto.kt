package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class WifiConfigDto(
    @SerializedName("ssid")     val ssid:     String,
    @SerializedName("password") val password: String?,
    @SerializedName("security") val security: String = "WPA2",
)
