package com.corall.agrotrack.presentation.users

import com.corall.agrotrack.data.remote.api.UsersApiService
import com.corall.agrotrack.data.remote.dto.UserCreateDto
import com.corall.agrotrack.data.remote.dto.UserItemDto
import com.corall.agrotrack.data.remote.dto.UserUpdateDto
import retrofit2.Response
import java.io.IOException

class FakeUsersApiService(
    initialUsers: MutableList<UserItemDto> = mutableListOf(),
) : UsersApiService {
    private val users = initialUsers
    var deleteShouldFail: Boolean = false
    var toggleShouldFail: Boolean = false
    var deleteCallCount: Int = 0

    override suspend fun listUsers(): Response<List<UserItemDto>> =
        Response.success(users.toList())

    override suspend fun createUser(body: UserCreateDto): Response<UserItemDto> {
        val created = UserItemDto(
            id       = (users.maxOfOrNull { it.id } ?: 0) + 1,
            names    = body.names,
            email    = body.email,
            roleId   = body.roleId,
            roleName = null,
            enable   = true,
        )
        users.add(created)
        return Response.success(created)
    }

    override suspend fun updateUser(id: Int, body: UserUpdateDto): Response<UserItemDto> {
        if (toggleShouldFail) throw IOException("network error")
        val idx = users.indexOfFirst { it.id == id }
        require(idx != -1) { "user $id not found in fake" }
        val current = users[idx]
        val updated = current.copy(
            names  = body.names  ?: current.names,
            email  = body.email  ?: current.email,
            roleId = body.roleId ?: current.roleId,
            enable = body.enable ?: current.enable,
        )
        users[idx] = updated
        return Response.success(updated)
    }

    override suspend fun deleteUser(id: Int): Response<Unit> {
        deleteCallCount++
        if (deleteShouldFail) throw IOException("network error")
        users.removeAll { it.id == id }
        return Response.success(Unit)
    }
}
