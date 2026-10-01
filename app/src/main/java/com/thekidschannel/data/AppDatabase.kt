package com.thekidschannel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RootEntity::class, ChannelProgressEntity::class, ChannelStatsEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rootDao(): RootDao
    abstract fun channelProgressDao(): ChannelProgressDao
    abstract fun channelStatsDao(): ChannelStatsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kids-channel.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channel_stats (
                channelUri TEXT NOT NULL,
                rootUri TEXT NOT NULL,
                name TEXT NOT NULL,
                watchTimeMs INTEGER NOT NULL,
                PRIMARY KEY(channelUri),
                FOREIGN KEY(rootUri) REFERENCES roots(uri) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_channel_stats_rootUri ON channel_stats(rootUri)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE roots ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE roots ADD COLUMN watchTimeMs INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `roots` (
                `uri` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`uri`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO `roots` (`uri`, `name`, `sortOrder`)
            SELECT `uri`, `name`, `sortOrder` FROM `channels`
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE `channels`")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `channel_progress` (
                `channelUri` TEXT NOT NULL,
                `rootUri` TEXT NOT NULL,
                `currentVideoUri` TEXT,
                `currentVideoIndex` INTEGER NOT NULL,
                `positionMs` INTEGER NOT NULL,
                PRIMARY KEY(`channelUri`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_channel_progress_rootUri` " +
                "ON `channel_progress` (`rootUri`)",
        )
    }
}
