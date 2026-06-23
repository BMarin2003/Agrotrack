package com.corall.agrotrack.data.remote.api

import com.corall.agrotrack.data.remote.dto.UserCreateDto
import com.corall.agrotrack.data.remote.dto.UserItemDto
import com.corall.agrotrack.data.remote.dto.UserUpdateDto
import retrofit2.Response
import retrofit2.http.*

interface UsersApiService {

    @GET("users")
    suspend fun listUsers(): Response<List<UserItemDto>>

    @POST("users")
    suspend fun createUser(@Body body: UserCreateDto): Response<UserItemDto>

    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body body: UserUpdateDto): Response<UserItemDto>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<Unit>
}
