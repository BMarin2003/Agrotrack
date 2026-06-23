package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserItemDto(
    @SerializedName("id")        val id:       Int,
    @SerializedName("names")     val names:    String,
    @SerializedName("email")     val email:    String,
    @SerializedName("role_id")   val roleId:   Int?,
    @SerializedName("role")      val roleName: String?,
    @SerializedName("enable")    val enable:   Boolean?,
)

data class UserCreateDto(
    @SerializedName("names")    val names:    String,
    @SerializedName("email")    val email:    String,
    @SerializedName("password") val password: String,
    @SerializedName("role_id")  val roleId:   Int,
)

data class UserUpdateDto(
    @SerializedName("names")   val names:   String?  = null,
    @SerializedName("email")   val email:   String?  = null,
    @SerializedName("role_id") val roleId:  Int?     = null,
    @SerializedName("enable")  val enable:  Boolean? = null,
)
