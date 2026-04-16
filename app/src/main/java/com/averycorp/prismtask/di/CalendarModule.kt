package com.averycorp.prismtask.di

import com.averycorp.prismtask.data.calendar.CalendarPushDispatcher
import com.averycorp.prismtask.data.calendar.DefaultCalendarPushDispatcher
import com.averycorp.prismtask.data.repository.CalendarEventRepository
import com.averycorp.prismtask.data.repository.CalendarSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the backend-mediated Google Calendar sync surface.
 * Both bindings point at concrete classes in `data.calendar` and
 * `data.repository` — consumers (repositories, workers, view models) only
 * see the small abstract interfaces so calendar wiring can be swapped in
 * tests without spinning up WorkManager or a Retrofit client.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {
    @Binds
    @Singleton
    abstract fun bindCalendarPushDispatcher(
        impl: DefaultCalendarPushDispatcher
    ): CalendarPushDispatcher

    @Binds
    @Singleton
    abstract fun bindCalendarEventRepository(
        impl: CalendarSyncRepository
    ): CalendarEventRepository
}
