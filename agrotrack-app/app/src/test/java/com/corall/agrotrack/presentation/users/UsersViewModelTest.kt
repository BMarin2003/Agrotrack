package com.corall.agrotrack.presentation.users

import com.corall.agrotrack.data.remote.dto.UserItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UsersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun userOf(id: Int, enable: Boolean = true) = UserItemDto(
        id = id, names = "User $id", email = "user$id@test.com",
        roleId = 2, roleName = "Operador", enable = enable,
    )

    @Test
    fun `requestDelete no llama a la API hasta confirmar`() = runTest {
        val fake = FakeUsersApiService(mutableListOf(userOf(1)))
        val vm = UsersViewModel(fake)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete(vm.uiState.value.users.first())
        assertEquals(0, fake.deleteCallCount)
        assertEquals(1, vm.uiState.value.confirmDeleteUser?.id)

        vm.confirmDelete()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fake.deleteCallCount)
        assertNull(vm.uiState.value.confirmDeleteUser)
        assertTrue(vm.uiState.value.users.isEmpty())
    }

    @Test
    fun `delete fallido deja un error visible y no rompe la lista`() = runTest {
        val fake = FakeUsersApiService(mutableListOf(userOf(1))).apply { deleteShouldFail = true }
        val vm = UsersViewModel(fake)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete(vm.uiState.value.users.first())
        vm.confirmDelete()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.users.size)
        assertTrue(vm.uiState.value.actionError != null)
    }

    @Test
    fun `toggleEnable fallido deja un error visible`() = runTest {
        val fake = FakeUsersApiService(mutableListOf(userOf(1))).apply { toggleShouldFail = true }
        val vm = UsersViewModel(fake)
        dispatcher.scheduler.advanceUntilIdle()

        vm.toggleEnable(vm.uiState.value.users.first())
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.actionError != null)
    }

    @Test
    fun `mientras una fila esta en proceso su id aparece en processingUserIds`() = runTest {
        val fake = FakeUsersApiService(mutableListOf(userOf(1)))
        val vm = UsersViewModel(fake)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete(vm.uiState.value.users.first())
        vm.confirmDelete()
        assertTrue(1 in vm.uiState.value.processingUserIds)

        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.processingUserIds.isEmpty())
    }

    @Test
    fun `save requiere nombre`() = runTest {
        val vm = UsersViewModel(FakeUsersApiService())
        dispatcher.scheduler.advanceUntilIdle()

        vm.openCreate()
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("password123")
        vm.save()

        assertEquals("El nombre es requerido", vm.uiState.value.dialogError)
    }

    @Test
    fun `save requiere password de al menos 8 caracteres al crear`() = runTest {
        val vm = UsersViewModel(FakeUsersApiService())
        dispatcher.scheduler.advanceUntilIdle()

        vm.openCreate()
        vm.onNameChange("Nuevo Usuario")
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("short")
        vm.save()

        assertEquals("La contraseña debe tener al menos 8 caracteres", vm.uiState.value.dialogError)
    }
}
