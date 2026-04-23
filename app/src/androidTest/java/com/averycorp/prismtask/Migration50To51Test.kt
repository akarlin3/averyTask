package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_50_51
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v50 → v51 (adds `updated_at INTEGER NOT
 * NULL DEFAULT 0` to five tables: `self_care_logs`, `leisure_logs`,
 * `self_care_steps`, `courses`, `course_completions`).
 */
@RunWith(AndroidJUnit4::class)
class Migration50To51Test {

    private val targetTables = listOf(
        "self_care_logs",
        "leisure_logs",
        "self_care_steps",
        "courses",
        "course_completions"
    )

    private fun openV50(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(50) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    for (t in targetTables) {
                        db.execSQL(
                            "CREATE TABLE `$t` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL" +
                                ")"
                        )
                    }
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
    fun allFiveTables_gainUpdatedAtColumn() {
        val helper = openV50()
        val db = helper.writableDatabase

        MIGRATION_50_51.migrate(db)

        for (t in targetTables) {
            db.query("PRAGMA table_info(`$t`)").use { c ->
                val names = mutableListOf<String>()
                while (c.moveToNext()) names.add(c.getString(1))
                assertTrue(
                    "table `$t` should have updated_at column, found $names",
                    names.contains("updated_at")
                )
            }
        }
        helper.close()
    }

    @Test
    fun existingRows_getZeroDefault() {
        val helper = openV50()
        val db = helper.writableDatabase

        for (t in targetTables) {
            db.execSQL("INSERT INTO `$t` (id) VALUES (1)")
        }

        MIGRATION_50_51.migrate(db)

        for (t in targetTables) {
            db.query("SELECT updated_at FROM `$t` WHERE id = 1").use { c ->
                assertTrue("no row in `$t`", c.moveToFirst())
                assertEquals("existing-row updated_at should default 0 in `$t`", 0L, c.getLong(0))
            }
        }
        helper.close()
    }

    @Test
    fun newInsert_canOverrideUpdatedAt() {
        val helper = openV50()
        val db = helper.writableDatabase

        MIGRATION_50_51.migrate(db)

        for ((i, t) in targetTables.withIndex()) {
            val stamp = 1700000000000L + i
            db.execSQL("INSERT INTO `$t` (id, updated_at) VALUES (${i + 10}, $stamp)")
            db.query("SELECT updated_at FROM `$t` WHERE id = ${i + 10}").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(stamp, c.getLong(0))
            }
        }
        helper.close()
    }
}
