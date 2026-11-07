// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.ukpratik.founderguru.data.Refresher

/**
 * Background refresh every 6 h (US-008, NFR-005). Returns success even when sources fail (their health records it)
 * and never writes `last_visit`.
 */
class RefreshWorker(context: Context, params: WorkerParameters, private val refresher: Refresher) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        refresher.refreshNow()
        return Result.success()
    }
}

/** The weekly / daily digest (US-012); the logic is in [DigestRunner]. */
class DigestWorker(context: Context, params: WorkerParameters, private val runner: DigestRunner) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runner.run(inputData.getLong(KEY_TARGET, System.currentTimeMillis()))
        return Result.success()
    }

    companion object {
        const val KEY_TARGET = "target"
    }
}

/** Manual DI for workers (ADR-004): WorkManager is configured by the application with this factory. */
class FgWorkerFactory(private val refresher: () -> Refresher, private val digest: () -> DigestRunner) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            RefreshWorker::class.java.name -> RefreshWorker(appContext, workerParameters, refresher())
            DigestWorker::class.java.name -> DigestWorker(appContext, workerParameters, digest())
            else -> null
        }
}
