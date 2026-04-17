package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.averycorp.prismtask.data.local.entity.FocusReleaseLogEntity

@Dao
interface FocusReleaseLogDao {
    @Insert
    suspend fun insert(log: FocusReleaseLogEntity): Long
}
