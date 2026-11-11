package tech.hezy.alarm.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.domain.AlarmTone
import tech.hezy.alarm.domain.LunarBirthday
import java.io.File
import java.time.DayOfWeek
import kotlin.math.abs

interface AlarmRepository {
    fun all(): List<Alarm>
    fun find(id: Long): Alarm?
    fun allocateId(): Long
    fun save(alarm: Alarm)
    fun delete(id: Long)
}

class FileAlarmRepository(context: Context) : AlarmRepository {
    private val file = File(context.filesDir, "alarms.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Any()
    private var snapshot = read()

    override fun all(): List<Alarm> = synchronized(lock) { snapshot.alarms.map { it.toModel() } }

    override fun find(id: Long): Alarm? = all().firstOrNull { it.id == id }

    override fun allocateId(): Long = synchronized(lock) {
        val id = snapshot.nextId
        snapshot = snapshot.copy(nextId = id + 1)
        write()
        id
    }

    override fun save(alarm: Alarm) = synchronized(lock) {
        val stored = alarm.toStored()
        val rest = snapshot.alarms.filterNot { it.id == alarm.id }
        snapshot = snapshot.copy(alarms = rest + stored)
        write()
    }

    override fun delete(id: Long) = synchronized(lock) {
        snapshot = snapshot.copy(alarms = snapshot.alarms.filterNot { it.id == id })
        write()
    }

    private fun read(): Snapshot = runCatching {
        if (!file.exists()) Snapshot() else json.decodeFromString<Snapshot>(file.readText())
    }.getOrDefault(Snapshot())

    private fun write() {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(snapshot))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

@Serializable
private data class Snapshot(val nextId: Long = 1, val alarms: List<StoredAlarm> = emptyList())

@Serializable
private data class StoredAlarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val label: String,
    val days: List<Int>,
    val tone: String,
    val snooze: Boolean,
    val snoozeMinutes: Int = 9,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val birthYear: Int? = null,
    val solar: Boolean = false,
    val contactId: Long? = null,
)

private fun StoredAlarm.toModel() = Alarm(
    id, hour, minute, enabled, label,
    days.map { DayOfWeek.of(it) }.toSet(),
    AlarmTone.fromKey(tone),
    snooze,
    snoozeMinutes,
    birthday = toBirthday(),
)

private fun StoredAlarm.toBirthday(): LunarBirthday? {
    val month = lunarMonth ?: return null
    val day = lunarDay ?: return null
    val year = birthYear?.takeIf { it in 1900..2100 }
    return if (solar) {
        if (month !in 1..12 || day !in 1..31) null else LunarBirthday(month, day, year, solar = true, contactId)
    } else {
        if (abs(month) !in 1..12 || day !in 1..30) null else LunarBirthday(month, day, year, contactId = contactId)
    }
}

private fun Alarm.toStored() = StoredAlarm(
    id, hour, minute, enabled, label,
    days.map { it.value }.sorted(),
    tone.storageKey,
    snooze,
    snoozeMinutes,
    birthday?.month,
    birthday?.day,
    birthday?.year,
    birthday?.solar ?: false,
    birthday?.contactId,
)
