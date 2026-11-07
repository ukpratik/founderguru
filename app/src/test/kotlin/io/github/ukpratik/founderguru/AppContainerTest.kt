// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** BUG-004 (US-007, NFR-004): thumbnails have no call timeout; feeds keep the 10 s policy (LLD §4.2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppContainerTest {
    @Test fun `image client has no call timeout, the feed client keeps 10 s`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val c = AppContainer(context)

        assertThat(c.httpClient.callTimeoutMillis).isEqualTo(10_000)
        assertThat(c.httpClient.followRedirects).isFalse()

        assertThat(c.imageClient.callTimeoutMillis).isEqualTo(0)
        assertThat(c.imageClient.connectTimeoutMillis).isEqualTo(10_000)
        assertThat(c.imageClient.readTimeoutMillis).isEqualTo(10_000)
        assertThat(c.imageClient.followRedirects).isTrue()
        assertThat(c.imageClient.interceptors).isEqualTo(c.httpClient.interceptors) // the same honest User-Agent
    }
}
