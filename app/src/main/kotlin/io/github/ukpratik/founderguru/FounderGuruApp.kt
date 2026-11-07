// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru

import android.app.Application
import androidx.work.Configuration
import io.github.ukpratik.founderguru.platform.DigestNotifier
import io.github.ukpratik.founderguru.work.FgWorkerFactory
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

class FounderGuruApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DigestNotifier.ensureChannel(this)
    }

    /** WorkManager with the app's worker factory (ADR-004); the default start-up initializer is removed in the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(FgWorkerFactory({ container.refresher }, { container.digestRunner }))
            .build()

    /** Coil (LLD §4.14): memory 15%, disk 100 MB in `cacheDir/images` (NFR-004), no crossfade. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.imageClient })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, IMAGE_MEMORY_PERCENT).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images").toOkioPath()).maxSizeBytes(IMAGE_DISK_BYTES).build() }
        .crossfade(false)
        .build()

    private companion object {
        const val IMAGE_MEMORY_PERCENT = 0.15
        const val IMAGE_DISK_BYTES = 100L * 1024 * 1024
    }
}
