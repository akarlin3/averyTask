package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_70_71
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v70 → v71 (additive `task_mode TEXT`
 * column on `tasks` for the Work / Play / Relax mode dimension — see
 * `docs/WORK_PLAY_RELAX.md`).
 *
 * Stripped-down v70 schema only includes the columns we need to verify
 * the migration; full schema isn't required because we're testing the
 * single ALTER TABLE.
 *
 * Covers:
 *  - Existing rows survive the migration with their original column data.
 *  - `task_mode` defaults to NULL on pre-existing rows (no retroactive
 *    auto-classification).
 *  - New writes can populate `task_mode` post-migration with each enum
 *    value.
 */
@RunWith(AndroidJUnit4::class)
class Migration70To71Test {

    private fun openV70(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(70) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE `tasks` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `cloud_id` TEXT,
                            `title` TEXT NOT NULL,
                            `description` TEXT,
                            `due_date` INTEGER,
                            `priority` INTEGER NOT NULL DEFAULT 0,
                            `is_completed` INTEGER NOT NULL DEFAULT 0,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `life_category` TEXT
                        )"""
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    // Migration invoked manually in each test.
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun migrate_addsTaskModeColumnWithNullDefault() {
        val helper = openV70()
        val db = helper.writableDatabase

        // Two existing rows: one with a life_category, one without. Neither
        // had a mode column on v70, so both should get task_mode = NULL.
        db.execSQL(
            "INSERT INTO tasks " +
                "(id, title, life_category, created_at, updated_at) " +
                "VALUES (1, 'existing work task', 'WORK', 100, 100)"
        )
        db.execSQL(
            "INSERT INTO tasks " +
                "(id, title, created_at, updated_at) " +
                "VALUES (2, 'plain task', 200, 200)"
        )

        MIGRATION_70_71.migrate(db)

        db.query(
            "SELECT id, title, life_category, task_mode FROM tasks ORDER BY id"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getLong(0))
            assertEquals("existing work task", c.getString(1))
            assertEquals("WORK", c.getString(2))
            assertTrue(
                "task_mode must default to NULL on pre-existing rows " +
                    "(no retroactive auto-classification)",
                c.isNull(3)
            )

            assertTrue(c.moveToNext())
            assertEquals(2, c.getLong(0))
            assertNull(c.getString(2))
            assertTrue(c.isNull(3))
        }
        helper.close()
    }

    @Test
    fun migrate_allowsWritingEachTaskModeValue() {
        val helper = openV70()
        val db = helper.writableDatabase
        MIGRATION_70_71.migrate(db)

        for ((id, mode) in listOf(10L to "WORK", 11L to "PLAY", 12L to "RELAX", 13L to "UNCATEGORIZED")) {
            db.execSQL(
                "INSERT INTO tasks (id, title, task_mode, created_at, updated_at) " +
                    "VALUES ($id, 'task $id', '$mode', $id, $id)"
            )
        }

        db.query("SELECT id, task_mode FROM tasks WHERE id >= 10 ORDER BY id").use { c ->
            val seen = mutableMapOf<Long, String?>()
            while (c.moveToNext()) seen[c.getLong(0)] = c.getString(1)
            assertEquals("WORK", seen[10L])
            assertEquals("PLAY", seen[11L])
            assertEquals("RELAX", seen[12L])
            assertEquals("UNCATEGORIZED", seen[13L])
        }
        helper.close()
    }
}
