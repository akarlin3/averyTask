package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_66_67
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v66 → v67 (additive `language_pick` /
 * `language_done` columns on `leisure_logs` for the new LANGUAGE built-in
 * leisure slot). Mirrors the pattern of [Migration51To52Test] — the project
 * ships with `exportSchema = false`, so a stripped-down v66 schema is built
 * via [SupportSQLiteOpenHelper], a row is seeded, the migration is invoked
 * directly, and the new columns are verified.
 *
 * Covers:
 *  - existing rows survive the migration with their original column data
 *  - `language_pick` defaults to NULL on existing rows
 *  - `language_done` defaults to 0 on existing rows
 *  - new writes can populate the new columns post-migration
 */
@RunWith(AndroidJUnit4::class)
class Migration66To67Test {

    private fun openV66(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(66) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE `leisure_logs` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `cloud_id` TEXT,
                            `date` INTEGER NOT NULL,
                            `music_pick` TEXT,
                            `music_done` INTEGER NOT NULL DEFAULT 0,
                            `flex_pick` TEXT,
                            `flex_done` INTEGER NOT NULL DEFAULT 0,
                            `custom_sections_state` TEXT,
                            `started_at` INTEGER,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL DEFAULT 0
                        )"""
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX `index_leisure_logs_date` ON `leisure_logs` (`date`)"
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
    fun migrate_addsLanguageColumnsWithSafeDefaults() {
        val helper = openV66()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO leisure_logs " +
                "(id, date, music_pick, music_done, flex_pick, flex_done, created_at, updated_at) " +
                "VALUES (1, 100, 'piano', 1, 'read', 0, 100, 100)"
        )

        MIGRATION_66_67.migrate(db)

        db.query(
            "SELECT music_pick, music_done, flex_pick, flex_done, language_pick, language_done " +
                "FROM leisure_logs WHERE id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("piano", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals("read", c.getString(2))
            assertEquals(0, c.getInt(3))
            assertTrue("language_pick must default to NULL on pre-existing rows", c.isNull(4))
            assertEquals("language_done must default to 0 on pre-existing rows", 0, c.getInt(5))
        }
        helper.close()
    }

    @Test
    fun migrate_allowsWritingNewLanguageColumns() {
        val helper = openV66()
        val db = helper.writableDatabase

        MIGRATION_66_67.migrate(db)

        db.execSQL(
            "INSERT INTO leisure_logs " +
                "(id, date, language_pick, language_done, created_at, updated_at) " +
                "VALUES (2, 200, 'italian', 1, 200, 200)"
        )

        db.query(
            "SELECT language_pick, language_done FROM leisure_logs WHERE id = 2"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("italian", c.getString(0))
            assertEquals(1, c.getInt(1))
        }
        helper.close()
    }
}
