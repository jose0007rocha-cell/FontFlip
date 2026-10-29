package app.fontflip

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fontflip.data.FontImportException
import app.fontflip.data.FontStore
import app.fontflip.install.FontApkBuilder
import app.fontflip.install.FontStatus
import app.fontflip.install.SystemActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val fonts: List<File> = emptyList(),
    val status: FontStatus = FontStatus.NONE,
    val busy: Boolean = false,
    val backupDone: Boolean = false,
)

sealed interface UiEvent {
    class Message(val res: Int, val args: List<Any> = emptyList()) : UiEvent
    class Install(val apk: File) : UiEvent
    data object Uninstall : UiEvent
    data object OpenRestore : UiEvent
}

class FontViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = FontStore(app)
    private val prefs = app.getSharedPreferences("flip", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(store.list(), SystemActions.status(app), prefs.getBoolean(BACKUP, false)),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var pendingApk: File? = null

    fun refreshStatus() {
        val status = SystemActions.status(app)
        _state.update { it.copy(status = status) }
        // Fonte acabou de ser instalada: leva direto para a restauração.
        if (status == FontStatus.CUSTOM && prefs.getBoolean(PENDING, false)) {
            prefs.edit().putBoolean(PENDING, false).apply()
            _events.tryEmit(UiEvent.OpenRestore)
        }
    }

    fun restoreTarget(): String? = prefs.getString(TARGET, null)

    fun saveRestoreTarget(value: String) {
        prefs.edit().putString(TARGET, value).apply()
    }

    fun setBackupDone(done: Boolean) {
        prefs.edit().putBoolean(BACKUP, done).apply()
        _state.update { it.copy(backupDone = done) }
    }

    private fun emitInstall(apk: File) {
        prefs.edit().putBoolean(PENDING, true).apply()
        _events.tryEmit(UiEvent.Install(apk))
    }

    fun importFonts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            for (uri in uris) {
                runCatching { withContext(Dispatchers.IO) { store.importFont(uri) } }
                    .onSuccess { ok++ }
                    .onFailure { e ->
                        _events.emit(UiEvent.Message((e as? FontImportException)?.res ?: R.string.err_import))
                    }
            }
            _state.update { it.copy(fonts = store.list()) }
            if (ok > 0) _events.emit(UiEvent.Message(R.string.msg_imported, listOf(ok)))
        }
    }

    fun delete(font: File) {
        store.delete(font)
        _state.update { it.copy(fonts = store.list()) }
    }

    /** Gera o APK; se já existe uma Samsung Sans (assinatura diferente), desinstala antes. */
    fun install(font: File) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val job = async(Dispatchers.IO) { FontApkBuilder.build(app, font) }
            val result = runCatching { withTimeout(BUILD_TIMEOUT_MS) { job.await() } }
            _state.update { it.copy(busy = false) }
            refreshStatus()
            result.onFailure {
                if (it is TimeoutCancellationException) {
                    job.cancel()
                    _events.emit(UiEvent.Message(R.string.err_timeout, listOf(FontApkBuilder.stage)))
                } else {
                    _events.emit(UiEvent.Message(R.string.err_build, listOf(it.message ?: it.javaClass.simpleName)))
                }
            }
            result.onSuccess { apk ->
                if (_state.value.status == FontStatus.NONE) {
                    emitInstall(apk)
                } else {
                    pendingApk = apk
                    _events.emit(UiEvent.Uninstall)
                }
            }
        }
    }

    fun uninstallCurrent() {
        pendingApk = null
        _events.tryEmit(UiEvent.Uninstall)
    }

    fun onUninstallResult() {
        refreshStatus()
        val apk = pendingApk ?: return
        pendingApk = null
        if (_state.value.status == FontStatus.NONE) {
            emitInstall(apk)
        } else {
            _events.tryEmit(UiEvent.Message(R.string.msg_uninstall_cancelled))
        }
    }
}

private const val BACKUP = "backup_done"
private const val PENDING = "pending_restore"
private const val TARGET = "restore_target"
private const val BUILD_TIMEOUT_MS = 60_000L
