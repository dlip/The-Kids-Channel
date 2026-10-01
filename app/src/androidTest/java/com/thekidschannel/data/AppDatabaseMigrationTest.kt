package com.thekidschannel.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationPreservesConfiguredRootsAndCreatesProgressStorage() = runBlocking {
        createVersionOneDatabase()

        database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

        val roots = database!!.rootDao().observeAll().first()
        assertEquals(
            listOf(RootEntity(ROOT_URI, "Kids", 0)),
            roots,
        )
        assertNull(database!!.channelProgressDao().get("content://kids/cartoons"))
    }

    @Test
    fun migrationFromVersionTwoPreservesRootsAndProgress() = runBlocking {
        createVersionOneDatabase()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(database: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    MIGRATION_1_2.migrate(database)
                }
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO channel_progress VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>("content://kids/cartoons", ROOT_URI, "video", 2, 5_000L),
            )
        }
        database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .build()
        assertEquals(listOf(RootEntity(ROOT_URI, "Kids", 0)), database!!.rootDao().observeAll().first())
        assertEquals(
            ChannelProgressEntity("content://kids/cartoons", ROOT_URI, "video", 2, 5_000),
            database!!.channelProgressDao().get("content://kids/cartoons"),
        )
    }

    @Test
    fun migrationFromVersionThreePreservesRootWatchTimeAndEnabledState() = runBlocking {
        createVersionOneDatabase()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(database: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    MIGRATION_1_2.migrate(database)
                    MIGRATION_2_3.migrate(database)
                }
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase.execSQL("UPDATE roots SET watchTimeMs = 60000, enabled = 0")
        }
        database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .addMigrations(MIGRATION_3_4)
            .build()
        assertEquals(
            listOf(RootEntity(ROOT_URI, "Kids", 0, enabled = false, watchTimeMs = 60_000)),
            database!!.rootDao().observeAll().first(),
        )
        assertEquals(emptyList<ChannelStatsEntity>(), database!!.channelStatsDao().observeAll().first())
    }

    @Test
    fun channelWatchTimeAccumulatesIntoItsRootAndSurvivesDisabling() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val roots = database!!.rootDao()
        val stats = database!!.channelStatsDao()
        roots.insert(RootEntity(ROOT_URI, "Kids", 0, watchTimeMs = 60_000))
        val cartoons = ChannelStatsEntity("content://kids/cartoons", ROOT_URI, "Cartoons")
        val music = ChannelStatsEntity("content://kids/music", ROOT_URI, "Music")
        stats.recordWatchTime(cartoons, 5_000)
        stats.recordWatchTime(cartoons, 2_000)
        stats.recordWatchTime(music, 3_000)
        stats.recordWatchTime(music, -1)
        stats.rememberChannel(cartoons.copy(name = "Renamed cartoons"))
        roots.setEnabled(ROOT_URI, false)
        assertEquals(70_000L, roots.observeAll().first().single().watchTimeMs)
        val channels = stats.observeAll().first().associateBy { it.channelUri }
        assertEquals(cartoons.copy(name = "Renamed cartoons", watchTimeMs = 7_000), channels[cartoons.channelUri])
        assertEquals(music.copy(watchTimeMs = 3_000), channels[music.channelUri])
        roots.delete(ROOT_URI)
        stats.recordWatchTime(cartoons, 1_000)
        assertEquals(emptyList<ChannelStatsEntity>(), stats.observeAll().first())
    }

    @Test
    fun rootToggleAndWatchTimePreservePlaybackProgress() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val roots = database!!.rootDao()
        val progress = database!!.channelProgressDao()
        roots.insert(RootEntity(ROOT_URI, "Kids", 0))
        val saved = ChannelProgressEntity("content://kids/cartoons", ROOT_URI, "video", 2, 5_000)
        progress.save(saved)
        roots.addWatchTime(ROOT_URI, 7_000)
        roots.addWatchTime(ROOT_URI, 3_000)
        roots.setEnabled(ROOT_URI, false)
        val disabled = roots.observeAll().first().single()
        assertFalse(disabled.enabled)
        assertEquals(10_000L, disabled.watchTimeMs)
        assertEquals(saved, progress.get(saved.channelUri))
        roots.setEnabled(ROOT_URI, true)
        assertEquals(RootEntity(ROOT_URI, "Kids", 0, watchTimeMs = 10_000), roots.observeAll().first().single())
    }

    private fun createVersionOneDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `channels` (
                                `uri` TEXT NOT NULL,
                                `name` TEXT NOT NULL,
                                `sortOrder` INTEGER NOT NULL,
                                `currentVideoUri` TEXT,
                                `currentVideoIndex` INTEGER NOT NULL,
                                `positionMs` INTEGER NOT NULL,
                                PRIMARY KEY(`uri`)
                            )
                            """.trimIndent(),
                        )
                        database.execSQL(
                            """
                            INSERT INTO `channels` (
                                `uri`, `name`, `sortOrder`, `currentVideoUri`,
                                `currentVideoIndex`, `positionMs`
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """.trimIndent(),
                            arrayOf<Any?>(ROOT_URI, "Kids", 0, null, 0, 0),
                        )
                    }

                    override fun onUpgrade(
                        database: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).apply {
            writableDatabase
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
        const val ROOT_URI = "content://kids"
    }
}
