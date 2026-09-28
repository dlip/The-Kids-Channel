package com.thekidschannel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RootEntity::class, ChannelProgressEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rootDao(): RootDao
    abstract fun channelProgressDao(): ChannelProgressDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kids-channel.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
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
