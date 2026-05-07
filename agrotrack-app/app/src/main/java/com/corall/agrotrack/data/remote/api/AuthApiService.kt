package com.corall.agrotrack.data.remote.api

import com.corall.agrotrack.data.remote.dto.LoginRequestDto
import com.corall.agrotrack.data.remote.dto.LoginResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("auth/verify-token")
    suspend fun verifyToken(): Response<LoginResponseDto>
}
