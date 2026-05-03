package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.ProjectRiskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectRiskDao {
    @Query("SELECT * FROM project_risks WHERE project_id = :projectId ORDER BY resolved_at IS NULL DESC, id ASC")
    fun observeRisks(projectId: Long): Flow<List<ProjectRiskEntity>>

    @Query(
        "SELECT * FROM project_risks WHERE project_id = :projectId AND resolved_at IS NULL ORDER BY id ASC"
    )
    fun observeActiveRisks(projectId: Long): Flow<List<ProjectRiskEntity>>

    @Query("SELECT * FROM project_risks WHERE project_id = :projectId ORDER BY id ASC")
    suspend fun getRisksOnce(projectId: Long): List<ProjectRiskEntity>

    @Query("SELECT * FROM project_risks ORDER BY project_id ASC, id ASC")
    suspend fun getAllRisksOnce(): List<ProjectRiskEntity>

    @Query("SELECT * FROM project_risks WHERE id = :id")
    suspend fun getByIdOnce(id: Long): ProjectRiskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(risk: ProjectRiskEntity): Long

    @Update
    suspend fun update(risk: ProjectRiskEntity)

    @Delete
    suspend fun delete(risk: ProjectRiskEntity)

    @Query("DELETE FROM project_risks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
