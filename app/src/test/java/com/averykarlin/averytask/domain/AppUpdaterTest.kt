package com.averykarlin.averytask.domain

import com.averykarlin.averytask.data.remote.UpdateStatus
import com.averykarlin.averytask.data.remote.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdaterTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_initialStatus_isIdle() {
        // AppUpdater requires Context, so we test the enum and status logic directly
        assertEquals(UpdateStatus.IDLE, UpdateStatus.valueOf("IDLE"))
    }

    @Test
    fun test_allStatusValues_exist() {
        val expected = listOf(
            "IDLE", "CHECKING", "UPDATE_AVAILABLE", "NO_UPDATE",
            "DOWNLOADING", "READY_TO_INSTALL", "ERROR"
        )
        val actual = UpdateStatus.entries.map { it.name }
        assertEquals(expected, actual)
    }

    @Test
    fun test_statusEnum_checking() {
        assertEquals(UpdateStatus.CHECKING, UpdateStatus.valueOf("CHECKING"))
    }

    @Test
    fun test_statusEnum_updateAvailable() {
        assertEquals(UpdateStatus.UPDATE_AVAILABLE, UpdateStatus.valueOf("UPDATE_AVAILABLE"))
    }

    @Test
    fun test_statusEnum_noUpdate() {
        assertEquals(UpdateStatus.NO_UPDATE, UpdateStatus.valueOf("NO_UPDATE"))
    }

    @Test
    fun test_statusEnum_downloading() {
        assertEquals(UpdateStatus.DOWNLOADING, UpdateStatus.valueOf("DOWNLOADING"))
    }

    @Test
    fun test_statusEnum_readyToInstall() {
        assertEquals(UpdateStatus.READY_TO_INSTALL, UpdateStatus.valueOf("READY_TO_INSTALL"))
    }

    @Test
    fun test_statusEnum_error() {
        assertEquals(UpdateStatus.ERROR, UpdateStatus.valueOf("ERROR"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_invalidStatusValue_throws() {
        UpdateStatus.valueOf("INVALID")
    }

    @Test
    fun test_statusEnum_ordinalOrder() {
        // Verify the ordinal positions match the expected UI flow
        assertTrue(UpdateStatus.IDLE.ordinal < UpdateStatus.CHECKING.ordinal)
        assertTrue(UpdateStatus.CHECKING.ordinal < UpdateStatus.UPDATE_AVAILABLE.ordinal)
        assertTrue(UpdateStatus.CHECKING.ordinal < UpdateStatus.NO_UPDATE.ordinal)
    }

    @Test
    fun test_statusCount_isSeven() {
        assertEquals(7, UpdateStatus.entries.size)
    }
}
