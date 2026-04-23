package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_49_50
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v49 → v50 (adds `completed_date_local TEXT`
 * to `habit_completions`, backfills from the existing `completed_date`
 * epoch via strftime, creates an index on the new column). The backfill
 * uses the migrating device's local timezone — tests assert on format
 * (`YYYY-MM-DD`) rather than a specific date to stay timezone-agnostic.
 */
@RunWith(AndroidJUnit4::class)
class Migration49To50Test {

    private fun openV49(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(49) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE `habit_completions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `habit_id` INTEGER NOT NULL,
                            `completed_date` INTEGER NOT NULL,
                            `created_at` INTEGER NOT NULL
                        )"""
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun backfill_populatesYyyyMmDd_forExistingRows() {
        val helper = openV49()
        val db = helper.writableDatabase

        // 2026-04-22 noon UTC (safe margin from day boundary under any local tz)
        val epochMillis = 1777212000000L
        db.execSQL(
            "INSERT INTO habit_completions (id, habit_id, completed_date, created_at) " +
                "VALUES (1, 1, $epochMillis, 1)"
        )

        MIGRATION_49_50.migrate(db)

        db.query("SELECT completed_date_local FROM habit_completions WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            val local = c.getString(0)
            assertNotNull("backfill must populate completed_date_local", local)
            assertTrue(
                "expected YYYY-MM-DD format, got $local",
                local.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))
            )
        }
        helper.close()
    }

    @Test
    fun index_createdOnCompletedDateLocal() {
        val helper = openV49()
        val db = helper.writableDatabase

        MIGRATION_49_50.migrate(db)

        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND tbl_name = 'habit_completions' AND name = ?",
            arrayOf("index_habit_completions_completed_date_local")
        ).use { c ->
            assertTrue(
                "expected index_habit_completions_completed_date_local to exist",
                c.moveToFirst()
            )
        }
        helper.close()
    }

    @Test
    fun newInsert_canProvideCompletedDateLocalDirectly() {
        val helper = openV49()
        val db = helper.writableDatabase

        MIGRATION_49_50.migrate(db)

        db.execSQL(
            "INSERT INTO habit_completions " +
                "(id, habit_id, completed_date, completed_date_local, created_at) " +
                "VALUES (5, 1, 1000, '2026-05-01', 1)"
        )

        db.query("SELECT completed_date_local FROM habit_completions WHERE id = 5").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("2026-05-01", c.getString(0))
        }
        helper.close()
    }

    @Test
    fun multipleRows_allBackfilled() {
        val helper = openV49()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO habit_completions (id, habit_id, completed_date, created_at) VALUES " +
                "(10, 1, 1000000000000, 1), " +
                "(11, 1, 1500000000000, 1), " +
                "(12, 2, 1700000000000, 1)"
        )

        MIGRATION_49_50.migrate(db)

        db.query(
            "SELECT COUNT(*) FROM habit_completions WHERE completed_date_local IS NULL"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("every row must be backfilled", 0, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun emptyTable_migratesWithoutError() {
        val helper = openV49()
        val db = helper.writableDatabase

        MIGRATION_49_50.migrate(db)

        db.query("SELECT COUNT(*) FROM habit_completions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        helper.close()
    }
}
