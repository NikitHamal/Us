package com.us.copilot.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * Destructive fallback is deliberately NOT enabled for upgrades: this database holds journals and
 * relationship history that the user cannot recreate, so a bad migration must fail loudly rather
 * than silently wipe their data.
 */
object Migrations {

    /** v1 -> v2: adds notification capture history. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `captured_notifications` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `packageName` TEXT NOT NULL,
                    `appLabel` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `postedAt` INTEGER NOT NULL,
                    `fingerprint` TEXT NOT NULL,
                    `sharedWithAi` INTEGER NOT NULL DEFAULT 0,
                    `riskLevel` TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_captured_notifications_postedAt` " +
                    "ON `captured_notifications` (`postedAt`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_captured_notifications_packageName` " +
                    "ON `captured_notifications` (`packageName`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_captured_notifications_fingerprint` " +
                    "ON `captured_notifications` (`fingerprint`)",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
