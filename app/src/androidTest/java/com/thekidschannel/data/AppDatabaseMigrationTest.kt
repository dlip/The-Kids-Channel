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
            .addMigrations(MIGRATION_1_2)
            .build()

        val roots = database!!.rootDao().observeAll().first()
        assertEquals(
            listOf(RootEntity(ROOT_URI, "Kids", 0)),
            roots,
        )
        assertNull(database!!.channelProgressDao().get("content://kids/cartoons"))
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
