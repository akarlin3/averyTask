package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.TaskDependencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDependencyDao {
    @Query("SELECT * FROM task_dependencies WHERE blocked_task_id = :taskId")
    fun observeBlockersOf(taskId: Long): Flow<List<TaskDependencyEntity>>

    @Query("SELECT * FROM task_dependencies WHERE blocker_task_id = :taskId")
    fun observeBlocking(taskId: Long): Flow<List<TaskDependencyEntity>>

    @Query("SELECT blocker_task_id FROM task_dependencies WHERE blocked_task_id = :taskId")
    suspend fun getBlockerIds(taskId: Long): List<Long>

    @Query("SELECT blocked_task_id FROM task_dependencies WHERE blocker_task_id = :taskId")
    suspend fun getBlockedIds(taskId: Long): List<Long>

    @Query("SELECT * FROM task_dependencies")
    suspend fun getAllDependenciesOnce(): List<TaskDependencyEntity>

    @Query("SELECT * FROM task_dependencies WHERE id = :id")
    suspend fun getByIdOnce(id: Long): TaskDependencyEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dependency: TaskDependencyEntity): Long

    @Delete
    suspend fun delete(dependency: TaskDependencyEntity)

    @Query("DELETE FROM task_dependencies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM task_dependencies WHERE blocker_task_id = :blockerId AND blocked_task_id = :blockedId")
    suspend fun deletePair(blockerId: Long, blockedId: Long)
}
