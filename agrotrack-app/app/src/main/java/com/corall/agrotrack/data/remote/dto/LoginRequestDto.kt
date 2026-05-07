package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginRequestDto(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String,
)

data class LoginResponseDto(
    @SerializedName("token")     val token: String,
    @SerializedName("expiresIn") val expiresIn: Int,
    @SerializedName("user")      val user: UserDto,
)

data class UserDto(
    @SerializedName("id")    val id: Int,
    @SerializedName("names") val names: String,
    @SerializedName("email") val email: String,
    @SerializedName("roles") val roles: List<RoleDto>,
)

data class RoleDto(
    @SerializedName("id")   val id: Int,
    @SerializedName("name") val name: String,
)
