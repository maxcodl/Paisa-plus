package com.paisaplus.compose.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MessageInboxViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: android.content.Context,
    private val inbox: MessageInboxRepository,
) : ViewModel() {
    val pending = inbox.pending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var feedback by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    private fun run(action: suspend () -> String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                feedback = runCatching { withContext(Dispatchers.IO) { action() } }
                    .getOrElse { it.message ?: "Could not process message" }
            } finally { busy = false }
        }
    }
    fun parse(text: String) = run { inbox.receive(text, "Pasted message", System.currentTimeMillis()) }
    /** Settings › SMS & AI › Scan past SMS (needs READ_SMS, asked by the screen). */
    fun scanSms(days: Int) = run { inbox.scanSmsHistory(ctx, days).let { (a, r, d) -> com.paisaplus.compose.str(com.paisaplus.compose.R.string.sms_scan_result_xyz, a, r, d) } }
    fun dismiss(id: String) = run { inbox.dismiss(id); "Message dismissed" }
    fun confirm(id: String, account: Long, category: Long, name: String) = run {
        inbox.confirm(id, account, category, name)
        "Transaction saved"
    }
}
