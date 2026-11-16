package com.panuwatki.safe.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.panuwatki.safe.SafeEngine
import com.panuwatki.safe.calls.RecordingWatcherWorker
import com.panuwatki.safe.data.AnalysisEntity
import com.panuwatki.safe.data.CsvExporter
import com.panuwatki.safe.data.Prefs
import com.panuwatki.safe.data.SafeDatabase
import com.panuwatki.safe.ml.Conversation
import com.panuwatki.safe.ml.ConversationState
import com.panuwatki.safe.ml.PiiRedactor
import com.panuwatki.safe.ml.RiskColor
import com.panuwatki.safe.speech.LiveListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val dao = SafeDatabase.get(app).analyses()

    var hasConsent by mutableStateOf(prefs.hasConsent)
        private set

    val history: StateFlow<List<AnalysisEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { SafeEngine.purgeExpired(app) }
    }

    fun acceptConsent() {
        prefs.consentVersion = Prefs.CURRENT_CONSENT_VERSION
        hasConsent = true
    }

    // ---------------- โหมดฟังสด (ทดลอง) ----------------
    data class LiveUi(
        val running: Boolean = false,
        val status: LiveListener.Status = LiveListener.Status.STOPPED,
        val partial: String = "",
        val lines: List<String> = emptyList(),
        val state: ConversationState? = null,
        val words: List<String> = emptyList(),
        val savedId: Long? = null,
    )

    var live by mutableStateOf(LiveUi())
        private set

    private var conversation: Conversation? = null
    private val listener = LiveListener(
        context = app,
        onText = { text -> onLiveText(text) },
        onPartial = { p -> live = live.copy(partial = p) },
        onStatus = { s -> live = live.copy(status = s, running = s == LiveListener.Status.LISTENING || s == LiveListener.Status.NO_SPEECH) },
    )

    fun startLive() {
        viewModelScope.launch {
            if (conversation == null) conversation = Conversation(SafeEngine.classifier(getApplication()))
            live = live.copy(running = true, savedId = null)
            listener.start()
        }
    }

    private fun onLiveText(text: String) {
        val conv = conversation ?: return
        viewModelScope.launch {
            val state = withContext(Dispatchers.Default) { conv.add(text) }
            val words = if (state.enoughData && state.peakColor != RiskColor.GREEN) {
                withContext(Dispatchers.Default) { SafeEngine.classifier(getApplication()).explain(state.transcript) }
            } else emptyList()
            live = live.copy(partial = "", lines = live.lines + text, state = state, words = words)
        }
    }

    /** หยุดฟัง แล้วบันทึกบทสนทนาลงประวัติ (ลบข้อมูลส่วนตัวแล้ว) เพื่อให้ผู้ใช้ยืนยันได้ */
    fun stopLive() {
        listener.stop()
        val state = live.state
        if (state != null && state.enoughData && live.savedId == null) {
            viewModelScope.launch {
                val id = SafeEngine.save(getApplication(), state, AnalysisEntity.SOURCE_LIVE)
                live = live.copy(savedId = id)
            }
        }
    }

    fun newCall() {
        stopLive()
        conversation?.reset()
        live = LiveUi()
    }

    // ---------------- วิเคราะห์ไฟล์เสียง ----------------
    sealed interface FileUi {
        data object Idle : FileUi
        data object Running : FileUi
        data class Done(val outcome: SafeEngine.RecordingOutcome) : FileUi
    }

    var fileUi by mutableStateOf<FileUi>(FileUi.Idle)
        private set

    fun analyzeFile(uri: Uri) {
        fileUi = FileUi.Running
        viewModelScope.launch {
            fileUi = FileUi.Done(SafeEngine.analyzeRecording(getApplication(), uri))
        }
    }

    fun resetFile() {
        fileUi = FileUi.Idle
    }

    // ---------------- ประวัติ + ติดป้ายข้อมูล ----------------
    suspend fun record(id: Long): AnalysisEntity? = dao.get(id)

    fun label(id: Long, label: Int, category: String?, editedTranscript: String) {
        viewModelScope.launch {
            dao.label(id, label, category, PiiRedactor.redact(editedTranscript.trim()))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    fun exportCsv(target: Uri, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val rows = dao.labeled()
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(target)?.use {
                    it.write(CsvExporter.toCsv(rows).toByteArray(Charsets.UTF_8))
                }
            }
            onDone(rows.size)
        }
    }

    // ---------------- การตั้งค่า ----------------
    var numberWarning by mutableStateOf(prefs.numberWarningEnabled)
        private set
    var autoRecording by mutableStateOf(prefs.autoRecordingCheckEnabled)
        private set

    fun setNumberWarning(on: Boolean) {
        prefs.numberWarningEnabled = on
        numberWarning = on
    }

    fun setAutoRecording(on: Boolean) {
        prefs.autoRecordingCheckEnabled = on
        autoRecording = on
        if (on) {
            prefs.lastRecordingScanMs = System.currentTimeMillis()
            RecordingWatcherWorker.schedule(getApplication())
        } else {
            RecordingWatcherWorker.cancel(getApplication())
        }
    }

    /** ลบทุกอย่างที่แอปเก็บไว้ และกลับไปหน้าขอความยินยอม */
    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            listener.stop()
            dao.deleteAll()
            WorkManager.getInstance(getApplication()).cancelAllWork()
            prefs.clearAll()
            withContext(Dispatchers.IO) { getApplication<Application>().cacheDir.deleteRecursively() }
            hasConsent = false
            numberWarning = prefs.numberWarningEnabled
            autoRecording = prefs.autoRecordingCheckEnabled
            live = LiveUi()
            fileUi = FileUi.Idle
            onDone()
        }
    }

    override fun onCleared() {
        listener.stop()
    }
}
