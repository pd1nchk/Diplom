package com.example.diplom.state



import com.example.diplom.api.CalendarEventDto



fun seedDefaultUserStates(storage: UserStateStorage) {

    seedCalendarIfMissing(

        storage,

        "9400888",

        listOf(

            CalendarEventDto(1L, "2026-05-12", "Сдать лабораторную по Kotlin", "9400888"),

            CalendarEventDto(2L, "2026-05-16", "Подготовить отчёт по практике", "9400888"),

        ),

    )

}



private fun seedCalendarIfMissing(

    storage: UserStateStorage,

    ticket: String,

    events: List<CalendarEventDto>,

) {

    if (storage.hasData(ticket)) return

    storage.saveCalendarEvents(ticket, events)

}

