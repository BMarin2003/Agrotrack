package com.corall.agrotrack.data.remote.api

import com.corall.agrotrack.data.remote.dto.TicketCreateDto
import com.corall.agrotrack.data.remote.dto.TicketDto
import com.corall.agrotrack.data.remote.dto.TicketStatusUpdateDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface HelpDeskApiService {

    @GET("helpdesk/tickets")
    suspend fun listTickets(): Response<List<TicketDto>>

    @POST("helpdesk/tickets")
    suspend fun createTicket(@Body body: TicketCreateDto): Response<TicketDto>

    @PUT("helpdesk/tickets/{id}/status")
    suspend fun updateTicketStatus(
        @Path("id") id: Long,
        @Body body: TicketStatusUpdateDto,
    ): Response<Unit>
}
