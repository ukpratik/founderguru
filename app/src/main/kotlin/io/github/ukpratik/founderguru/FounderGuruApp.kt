// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru

import android.app.Application

class FounderGuruApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
