package com.averykarlin.averytask.data.repository

import com.averykarlin.averytask.data.local.dao.TagDao
import com.averykarlin.averytask.data.local.entity.TagEntity
import com.averykarlin.averytask.data.local.entity.TaskTagCrossRef
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao
) {
    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    fun searchTags(query: String): Flow<List<TagEntity>> = tagDao.searchTags(query)

    suspend fun addTag(name: String, color: String = "#6B7280"): Long =
        tagDao.insert(TagEntity(name = name, color = color))

    suspend fun updateTag(tag: TagEntity) = tagDao.update(tag)

    suspend fun deleteTag(tag: TagEntity) = tagDao.delete(tag)

    fun getTagsForTask(taskId: Long): Flow<List<TagEntity>> = tagDao.getTagsForTask(taskId)

    suspend fun setTagsForTask(taskId: Long, tagIds: List<Long>) {
        tagDao.removeAllTagsFromTask(taskId)
        tagIds.forEach { tagId ->
            tagDao.addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))
        }
    }

    suspend fun addTagToTask(taskId: Long, tagId: Long) {
        tagDao.addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))
    }

    suspend fun removeTagFromTask(taskId: Long, tagId: Long) {
        tagDao.removeTagFromTask(taskId, tagId)
    }
}
