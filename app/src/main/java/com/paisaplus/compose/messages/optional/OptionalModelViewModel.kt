package com.paisaplus.compose.messages.optional

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OptionalModelViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val store: OptionalModelStore,
    private val settings: com.paisaplus.compose.data.SettingsStore,
) : ViewModel() {
    private val work = WorkManager.getInstance(context)
    val specification = store.specification
    val installed = store.installed
    val download = work.getWorkInfosForUniqueWorkFlow(MODEL_DOWNLOAD_WORK)
        .map { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.maxByOrNull { it.runAttemptCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _draft = MutableStateFlow<String?>(null)
    val draft = _draft.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private var job: Job? = null

    /** Waits for Wi-Fi (unmetered) when Settings › Message import › Wi-Fi only is on. */
    fun downloadModel() {
        _message.value = null
        viewModelScope.launch {
            val net = if (settings.messages.first().wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            work.enqueueUniqueWork(MODEL_DOWNLOAD_WORK, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(net).build()).build())
        }
    }
    fun cancelDownload() { work.cancelUniqueWork(MODEL_DOWNLOAD_WORK) }
    fun cancelTask() { job?.cancel() }
    private fun task(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true; _message.value = null
        job = viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { _message.value = "Model task cancelled"; throw e }
            catch (e: Exception) { _message.value = e.message ?: "Model task failed" }
            finally { _busy.value = false }
        }
    }
    fun importPack(uri: Uri) = task { store.importPack(uri); _message.value = "Optional model installed" }
    fun remove() = task { store.delete(); _draft.value = null; _message.value = "Optional model deleted" }
    fun test(text: String) = task { _draft.value = null; _draft.value = store.generate(text) }
}
