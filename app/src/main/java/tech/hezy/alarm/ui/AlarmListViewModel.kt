package tech.hezy.alarm.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.hezy.alarm.data.AlarmRepository
import tech.hezy.alarm.data.AlarmScheduler
import tech.hezy.alarm.data.AlarmSoundPlayer
import tech.hezy.alarm.data.ContactBook
import tech.hezy.alarm.data.ContactCandidate
import tech.hezy.alarm.data.ContactNameMatch
import tech.hezy.alarm.data.alreadyImported
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.domain.AlarmTone
import tech.hezy.alarm.domain.LunarBirthday
import tech.hezy.alarm.mvi.MviViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

data class EditorDraft(
    val id: Long,
    val isNew: Boolean,
    val hour: Int,
    val minute: Int,
    val label: String,
    val days: Set<DayOfWeek>,
    val tone: AlarmTone,
    val snooze: Boolean,
    val page: EditorPage,
    val snoozeMinutes: Int = 9,
    val birthday: LunarBirthday? = null,
)

enum class EditorPage { Form, Repeat, Label, Sound, Snooze, Export }

enum class ContactNotice { Wrote, Failed, NeedName, Denied }

data class AlarmListState(
    val alarms: List<Alarm> = emptyList(),
    val editing: Boolean = false,
    val armedDeleteId: Long? = null,
    val editor: EditorDraft? = null,
    val imports: List<ContactCandidate>? = null,
    val exportMatches: List<ContactNameMatch>? = null,
    val loadingImports: Boolean = false,
    val loadingExport: Boolean = false,
    val writingContact: Boolean = false,
    val contactNotice: ContactNotice? = null,
)

sealed interface AlarmIntent {
    data object ToggleEdit : AlarmIntent
    data class ToggleEnabled(val id: Long) : AlarmIntent
    data class ArmDelete(val id: Long) : AlarmIntent
    data class Delete(val id: Long) : AlarmIntent
    data class Open(val id: Long?) : AlarmIntent
    data object OpenBirthday : AlarmIntent
    data object CloseEditor : AlarmIntent
    data class ChangeTime(val hour: Int, val minute: Int) : AlarmIntent
    data class OpenPage(val page: EditorPage) : AlarmIntent
    data class ToggleDay(val day: DayOfWeek) : AlarmIntent
    data class ChangeLabel(val value: String) : AlarmIntent
    data class SelectTone(val tone: AlarmTone) : AlarmIntent
    data class ToggleSnooze(val enabled: Boolean) : AlarmIntent
    data class ChangeSnoozeMinutes(val minutes: Int) : AlarmIntent
    data class ChangeLunarMonth(val month: Int) : AlarmIntent
    data class ChangeLunarDay(val day: Int) : AlarmIntent
    data class ToggleLeap(val on: Boolean) : AlarmIntent
    data class ToggleBirthYear(val on: Boolean) : AlarmIntent
    data class ChangeBirthYear(val year: Int) : AlarmIntent
    data object Save : AlarmIntent
    data object ShowImports : AlarmIntent
    data object CloseImports : AlarmIntent
    data class ImportOne(val candidate: ContactCandidate) : AlarmIntent
    data class ExportTo(val contactId: Long?, val create: Boolean) : AlarmIntent
    data class ExportPicked(val uri: String) : AlarmIntent
    data object ContactsDenied : AlarmIntent
    data object ClearContactNotice : AlarmIntent
}

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val player: AlarmSoundPlayer,
    private val contacts: ContactBook,
    private val defaultLabel: String,
) : MviViewModel<AlarmListState, AlarmIntent>(AlarmListState()) {
    private var preview: Job? = null
    private var importEpoch = 0
    private var exportEpoch = 0
    private var writeEpoch = 0

    init {
        publish()
    }

    override fun reduce(intent: AlarmIntent) {
        when (intent) {
            AlarmIntent.ToggleEdit -> update {
                copy(editing = !editing, armedDeleteId = null)
            }
            is AlarmIntent.ToggleEnabled -> {
                val alarm = repository.find(intent.id) ?: return
                val next = alarm.copy(enabled = !alarm.enabled)
                repository.save(next)
                scheduler.schedule(next)
                publish()
            }
            is AlarmIntent.ArmDelete -> update { copy(armedDeleteId = intent.id) }
            is AlarmIntent.Delete -> {
                scheduler.cancel(intent.id)
                repository.delete(intent.id)
                if (state.value.editor?.id == intent.id) stopPreview()
                update { copy(armedDeleteId = null, editor = editor?.takeUnless { it.id == intent.id }) }
                publish()
            }
            is AlarmIntent.Open -> openEditor(intent.id, birthday = false)
            AlarmIntent.OpenBirthday -> openEditor(null, birthday = true)
            AlarmIntent.CloseEditor -> {
                stopPreview()
                exportEpoch++
                writeEpoch++
                update { copy(editor = null, exportMatches = null, loadingExport = false, writingContact = false) }
            }
            is AlarmIntent.ChangeTime -> updateEditor { copy(hour = intent.hour, minute = intent.minute) }
            is AlarmIntent.OpenPage -> {
                if (intent.page != EditorPage.Sound) stopPreview()
                val exporting = intent.page == EditorPage.Export
                update {
                    copy(
                        editor = editor?.copy(page = intent.page),
                        exportMatches = if (exporting) null else exportMatches,
                        loadingExport = exporting,
                        contactNotice = if (exporting) null else contactNotice,
                    )
                }
                if (exporting) loadExportMatches()
            }
            is AlarmIntent.ToggleDay -> updateEditor {
                val days = if (intent.day in this.days) this.days - intent.day else this.days + intent.day
                copy(days = days)
            }
            is AlarmIntent.ChangeLabel -> updateEditor { copy(label = intent.value) }
            is AlarmIntent.SelectTone -> {
                updateEditor { copy(tone = intent.tone) }
                stopPreview()
                player.play(intent.tone)
                preview = viewModelScope.launch {
                    delay(1_600)
                    player.stop()
                }
            }
            is AlarmIntent.ToggleSnooze -> updateEditor { copy(snooze = intent.enabled) }
            is AlarmIntent.ChangeSnoozeMinutes -> {
                if (intent.minutes in 1..15) updateEditor { copy(snoozeMinutes = intent.minutes) }
            }
            is AlarmIntent.ChangeLunarMonth -> updateEditor {
                val birthday = birthday ?: return@updateEditor this
                val sign = if (!birthday.solar && birthday.month < 0) -1 else 1
                copy(birthday = birthday.copy(month = sign * intent.month.coerceIn(1, 12)))
            }
            is AlarmIntent.ChangeLunarDay -> updateEditor {
                val birthday = birthday ?: return@updateEditor this
                val maxDay = if (birthday.solar) 31 else 30
                copy(birthday = birthday.copy(day = intent.day.coerceIn(1, maxDay)))
            }
            is AlarmIntent.ToggleLeap -> updateEditor {
                val birthday = birthday ?: return@updateEditor this
                if (birthday.solar) return@updateEditor this
                val month = abs(birthday.month)
                copy(birthday = birthday.copy(month = if (intent.on) -month else month))
            }
            is AlarmIntent.ToggleBirthYear -> updateEditor {
                val birthday = birthday ?: return@updateEditor this
                val year = if (intent.on) (birthday.year ?: LocalDate.now().year).coerceIn(1900, 2100) else null
                copy(birthday = birthday.copy(year = year))
            }
            is AlarmIntent.ChangeBirthYear -> updateEditor {
                val birthday = birthday ?: return@updateEditor this
                copy(birthday = birthday.copy(year = intent.year.coerceIn(1900, 2100)))
            }
            AlarmIntent.Save -> {
                val draft = state.value.editor ?: return
                stopPreview()
                val alarm = draft.toAlarm()
                repository.save(alarm)
                scheduler.schedule(alarm)
                update { copy(editor = null) }
                publish()
            }
            AlarmIntent.ShowImports -> loadImports()
            AlarmIntent.CloseImports -> {
                importEpoch++
                update { copy(imports = null, loadingImports = false) }
            }
            is AlarmIntent.ImportOne -> importOne(intent.candidate)
            is AlarmIntent.ExportTo -> export(intent.contactId, intent.create)
            is AlarmIntent.ExportPicked -> {
                val uri = runCatching { Uri.parse(intent.uri) }.getOrNull() ?: return
                viewModelScope.launch(Dispatchers.IO) {
                    val id = contacts.contactId(uri)
                    withContext(Dispatchers.Main) {
                        if (id == null) update { copy(contactNotice = ContactNotice.Failed) }
                        else export(id, create = false)
                    }
                }
            }
            AlarmIntent.ContactsDenied -> update { copy(contactNotice = ContactNotice.Denied) }
            AlarmIntent.ClearContactNotice -> update { copy(contactNotice = null) }
        }
    }

    private fun openEditor(id: Long?, birthday: Boolean) {
        player.stop()
        val now = LocalTime.now()
        val existing = id?.let(repository::find)
        val draft = when {
            existing == null && birthday -> EditorDraft(
                id = repository.allocateId(),
                isNew = true,
                hour = 9,
                minute = 0,
                label = "",
                days = emptySet(),
                tone = AlarmTone.Rise,
                snooze = true,
                page = EditorPage.Form,
                birthday = LunarBirthday(1, 1, null),
            )
            existing == null -> EditorDraft(
                id = repository.allocateId(),
                isNew = true,
                hour = now.hour,
                minute = now.minute,
                label = defaultLabel,
                days = emptySet(),
                tone = AlarmTone.Rise,
                snooze = true,
                page = EditorPage.Form,
            )
            else -> existing.toDraft(isNew = false)
        }
        importEpoch++
        update { copy(editor = draft, editing = false, armedDeleteId = null, imports = null, loadingImports = false) }
    }

    private fun loadImports() {
        val epoch = ++importEpoch
        update { copy(imports = emptyList(), loadingImports = true, contactNotice = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val found = contacts.birthdays()
            withContext(Dispatchers.Main) {
                if (epoch != importEpoch) return@withContext
                update { copy(imports = found, loadingImports = false) }
            }
        }
    }

    private fun importOne(candidate: ContactCandidate) {
        if (alreadyImported(repository.all(), candidate)) return
        val birthday = candidate.birthday
        val alarm = Alarm(
            id = repository.allocateId(),
            hour = 9,
            minute = 0,
            enabled = true,
            label = candidate.name,
            days = emptySet(),
            tone = AlarmTone.Rise,
            snooze = true,
            birthday = birthday.copy(contactId = candidate.contactId),
        )
        repository.save(alarm)
        scheduler.schedule(alarm)
        publish()
    }

    private fun loadExportMatches() {
        val name = state.value.editor?.label.orEmpty()
        val epoch = ++exportEpoch
        update { copy(exportMatches = null, loadingExport = true, contactNotice = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val found = contacts.matches(name)
            withContext(Dispatchers.Main) {
                if (epoch != exportEpoch) return@withContext
                update { copy(exportMatches = found, loadingExport = false) }
            }
        }
    }

    private fun export(contactId: Long?, create: Boolean) {
        val draft = state.value.editor ?: return
        val birthday = draft.birthday ?: return
        if (state.value.writingContact) return
        val name = draft.label.trim()
        if (create && name.isEmpty()) {
            update { copy(contactNotice = ContactNotice.NeedName) }
            return
        }
        if (!create && contactId == null) return
        val epoch = ++writeEpoch
        update { copy(writingContact = true, contactNotice = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val id = contacts.write(name, birthday, contactId, create)
            withContext(Dispatchers.Main) {
                if (epoch != writeEpoch) {
                    update { copy(writingContact = false) }
                    return@withContext
                }
                if (id == null) {
                    update { copy(writingContact = false, contactNotice = ContactNotice.Failed) }
                    return@withContext
                }
                updateEditor { copy(birthday = this.birthday?.copy(contactId = id)) }
                val saved = state.value.editor
                if (saved != null && !saved.isNew) {
                    val alarm = saved.toAlarm()
                    repository.save(alarm)
                    scheduler.schedule(alarm)
                    publish()
                }
                update { copy(writingContact = false, contactNotice = ContactNotice.Wrote) }
            }
        }
    }

    private fun publish() {
        val alarms = repository.all().sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        update { copy(alarms = alarms) }
    }

    private fun updateEditor(block: EditorDraft.() -> EditorDraft) {
        update { copy(editor = editor?.block()) }
    }

    private fun stopPreview() {
        preview?.cancel()
        player.stop()
    }
}

private fun Alarm.toDraft(isNew: Boolean) = EditorDraft(
    id, isNew, hour, minute, label, days, tone, snooze, EditorPage.Form, snoozeMinutes, birthday,
)

private fun EditorDraft.toAlarm() = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    enabled = true,
    label = label.ifBlank { "" },
    days = if (birthday == null) days else emptySet(),
    tone = tone,
    snooze = snooze,
    snoozeMinutes = snoozeMinutes,
    birthday = birthday,
)
