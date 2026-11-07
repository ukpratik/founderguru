// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * `founderguru.db` (LLD §2.1). Version 1 may change freely until the first public release; after that every change
 * bumps the version, commits the schema and ships a tested migration (NFR-012). Never a destructive fallback.
 */
@Database(entities = [StoryEntity::class, SourceStateEntity::class], version = 1, exportSchema = true)
abstract class FgDatabase : RoomDatabase() {
    abstract fun stories(): StoryDao
    abstract fun sources(): SourceStateDao

    companion object {
        const val NAME = "founderguru.db"

        fun build(context: Context): FgDatabase =
            Room.databaseBuilder(context.applicationContext, FgDatabase::class.java, NAME).build()
    }
}
