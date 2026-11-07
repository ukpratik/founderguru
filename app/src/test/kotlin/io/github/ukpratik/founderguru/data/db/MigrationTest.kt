// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The migration harness (NFR-012, LLD §2.1). The schema is still version 1 (M4 added no columns), so this checks
 * that a database created from the committed `1.json` opens with the current Room classes and keeps saved stories
 * and `source_state.enabled`. Each future version adds a `runMigrationsAndValidate(n, …)` step here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        FgDatabase::class.java,
    )

    @Test fun `version 1 from the committed schema opens with saved stories and source switches intact`() = runBlocking<Unit> {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO source_state (id, region, url, enabled, consecutiveFailures) VALUES ('inc42', 'IN', 'https://inc42.com/feed/', 0, 0)")
            execSQL(
                """INSERT INTO stories (sourceId, sourceName, url, urlKey, title, titleKey, snippet, imageUrl, publishedAt, firstSeenAt,
                   lenses, sectors, regionMentions, savedAt, openedAt)
                   VALUES ('inc42', 'Inc42', 'https://inc42.com/a', 'inc42.com/a', 'A saved story', 'a saved story', '', NULL, 1, 1, 1, 0, 0, 99, NULL)""",
            )
            close()
        }
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), FgDatabase::class.java, name)
            .allowMainThreadQueries().build()
        try {
            assertThat(db.stories().saved().first().map { it.title }).containsExactly("A saved story")
            assertThat(db.sources().byId("inc42")!!.enabled).isFalse()
        } finally {
            db.close()
        }
    }
}
