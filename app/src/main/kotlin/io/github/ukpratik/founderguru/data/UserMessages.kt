// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-level one-off messages shown as a snackbar on Home (LLD §5: messages are state, cleared once shown). */
enum class UserMessage { DIGEST_OFF }

class UserMessages {
    private val _current = MutableStateFlow<UserMessage?>(null)
    val current: StateFlow<UserMessage?> = _current.asStateFlow()

    fun show(message: UserMessage) {
        _current.value = message
    }

    fun shown(message: UserMessage) {
        _current.compareAndSet(message, null)
    }
}
