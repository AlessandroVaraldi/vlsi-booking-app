package com.example.labdesks.data

class DeskRepository(
    private val api: Api
) {
    suspend fun loadDesks(day: String): List<DeskStatus> = api.getDesks(day)

    suspend fun bookDesk(day: String, deskId: Int, name: String, am: Boolean, pm: Boolean) =
        api.createBooking(
            BookingCreate(
                desk_id = deskId,
                day = day,
                booked_by = name,
                am = am,
                pm = pm
            )
        )
}
