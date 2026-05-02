package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.seed.AutomationStarterLibrary
import com.averycorp.prismtask.data.seed.AutomationTemplateCategory
import com.averycorp.prismtask.domain.automation.AutomationAction
import com.averycorp.prismtask.domain.automation.AutomationCondition
import com.averycorp.prismtask.domain.automation.AutomationTrigger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTemplateRepositoryTest {

    private val ruleRepository: AutomationRuleRepository = mockk(relaxed = true)
    private val repo = AutomationTemplateRepository(ruleRepository)

    @Test fun templates_returnsEntireLibrary() {
        assertEquals(
            AutomationStarterLibrary.ALL_TEMPLATES.size,
            repo.templates().size
        )
    }

    @Test fun templatesByCategory_groupsAllTemplates() {
        val byCategory = repo.templatesByCategory()
        for (cat in AutomationTemplateCategory.values()) {
            val rules = byCategory[cat]
            assertNotNull("category ${cat.name} missing", rules)
            assertTrue("category ${cat.name} empty", rules!!.isNotEmpty())
        }
        // Sum of grouped sizes equals total inventory.
        val total = byCategory.values.sumOf { it.size }
        assertEquals(repo.templates().size, total)
    }

    @Test fun search_emptyQuery_returnsAll() {
        assertEquals(repo.templates().size, repo.search("").size)
        assertEquals(repo.templates().size, repo.search("   ").size)
    }

    @Test fun search_matchesNameAndDescription_caseInsensitive() {
        val urgent = repo.search("urgent")
        assertTrue(urgent.any { it.id == "builtin.notify_overdue_urgent" })
        // Streak rules should match on description even if "streak" isn't in
        // every name.
        val streak = repo.search("streak")
        assertTrue(streak.size >= 3)
    }

    @Test fun findById_resolvesRealTemplate() {
        val t = repo.findById("builtin.morning_routine")
        assertNotNull(t)
        assertEquals(AutomationTemplateCategory.STAY_ON_TOP, t!!.category)
    }

    @Test fun findById_unknownId_returnsNull() {
        assertNull(repo.findById("starter.does.not.exist"))
    }

    @Test fun importTemplate_createsRule_disabled_with_templateKey() = runBlocking {
        coEvery {
            ruleRepository.create(
                name = any(),
                description = any(),
                trigger = any(),
                condition = any(),
                actions = any(),
                priority = any(),
                enabled = any(),
                isBuiltIn = any(),
                templateKey = any()
            )
        } returns 42L

        val templateId = "starter.friction.auto_flag_urgent"
        val newId = repo.importTemplate(templateId)
        assertEquals(42L, newId)
        coVerify(exactly = 1) {
            ruleRepository.create(
                name = "Auto-Flag Urgent Tasks",
                description = any(),
                trigger = any<AutomationTrigger.EntityEvent>(),
                condition = any<AutomationCondition>(),
                actions = match<List<AutomationAction>> { actions ->
                    actions.size == 1 && actions[0] is AutomationAction.MutateTask
                },
                priority = 0,
                enabled = false,
                isBuiltIn = false,
                templateKey = templateId
            )
        }
    }

    @Test fun importTemplate_unknownId_returnsNullAndDoesNotCreate() = runBlocking {
        val newId = repo.importTemplate("starter.bogus.id")
        assertNull(newId)
        coVerify(exactly = 0) {
            ruleRepository.create(
                name = any(),
                description = any(),
                trigger = any(),
                condition = any(),
                actions = any(),
                priority = any(),
                enabled = any(),
                isBuiltIn = any(),
                templateKey = any()
            )
        }
        // sanity — search still works
        assertFalse(repo.search("bogus").any { it.id == "starter.bogus.id" })
    }
}
