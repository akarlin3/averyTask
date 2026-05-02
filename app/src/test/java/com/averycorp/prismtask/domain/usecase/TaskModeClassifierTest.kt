package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.preferences.TaskModeCustomKeywords
import com.averycorp.prismtask.domain.model.TaskMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskModeClassifierTest {
    private val classifier = TaskModeClassifier()

    @Test
    fun `empty input is uncategorized`() {
        assertEquals(TaskMode.UNCATEGORIZED, classifier.classify(""))
        assertEquals(TaskMode.UNCATEGORIZED, classifier.classify("   "))
    }

    @Test
    fun `work keyword classifies as work`() {
        assertEquals(TaskMode.WORK, classifier.classify("Send the invoice"))
        assertEquals(TaskMode.WORK, classifier.classify("Finish quarterly report"))
    }

    @Test
    fun `play keyword classifies as play`() {
        assertEquals(TaskMode.PLAY, classifier.classify("Hike with friends"))
        assertEquals(TaskMode.PLAY, classifier.classify("Paint the spare room mural"))
    }

    @Test
    fun `relax keyword classifies as relax`() {
        assertEquals(TaskMode.RELAX, classifier.classify("Take a nap"))
        assertEquals(TaskMode.RELAX, classifier.classify("Long bath after dinner"))
    }

    @Test
    fun `unrelated text is uncategorized`() {
        assertEquals(TaskMode.UNCATEGORIZED, classifier.classify("Random unrelated string"))
    }

    @Test
    fun `tie breaks toward relax then play then work`() {
        // "play" + "rest" both match — tie-break wins for RELAX.
        assertEquals(TaskMode.RELAX, classifier.classify("play and rest"))
        // "play" + "ship" both match — tie-break wins for PLAY (over WORK).
        assertEquals(TaskMode.PLAY, classifier.classify("play and ship"))
    }

    @Test
    fun `description text is also scanned`() {
        assertEquals(
            TaskMode.RELAX,
            classifier.classify(title = "After-work activity", description = "lie down and breathe")
        )
    }

    @Test
    fun `custom keywords augment defaults`() {
        val classifier = TaskModeClassifier.withCustomKeywords(
            TaskModeCustomKeywords(work = "deck, slides", play = "lego")
        )
        assertEquals(TaskMode.WORK, classifier.classify("Update the deck"))
        assertEquals(TaskMode.PLAY, classifier.classify("Build a Lego set"))
    }

    @Test
    fun `case insensitive`() {
        assertEquals(TaskMode.WORK, classifier.classify("REVIEW the PR"))
    }
}
