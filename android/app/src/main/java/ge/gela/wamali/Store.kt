package ge.gela.wamali

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs

data class DoseTime(val hour: Int, val minute: Int) {
    val label: String get() = "%02d:%02d".format(hour, minute)
}

data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val dose: String = "",
    val note: String = "",
    val times: List<DoseTime> = listOf(DoseTime(9, 0)),
    val weekdays: Set<Int> = (1..7).toSet(),   // Calendar.DAY_OF_WEEK: 1 = კვირა
    val stock: Int? = null,
    val lowStock: Int = 5,
    val photo: String? = null,
    val active: Boolean = true,
) {
    val timesSummary: String
        get() = times.sortedBy { it.hour * 60 + it.minute }.joinToString(", ") { it.label }
}

data class DoseLog(val medId: String, val medName: String, val scheduled: Long, val time: Long, val action: String)

data class DoseSlot(val med: Medication, val time: DoseTime, val at: Long)

const val TAKEN = "taken"
const val SNOOZED = "snoozed"
const val SKIPPED = "skipped"

val DAY_SHORT = arrayOf("", "კვ", "ორ", "სა", "ოთ", "ხუ", "პა", "შა")
val WEEK_ORDER = listOf(2, 3, 4, 5, 6, 7, 1)

object UiState {
    var resumeTick by mutableIntStateOf(0)
}

object MedStore {
    val meds = mutableStateListOf<Medication>()
    val logs = mutableStateListOf<DoseLog>()
    private var dir: File? = null

    fun init(ctx: Context) {
        if (dir != null) return
        dir = ctx.applicationContext.filesDir
        load()
    }

    private val file: File get() = File(dir, "data.json")

    private fun load() {
        val f = file
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText())
            val ma = o.optJSONArray("meds") ?: JSONArray()
            for (i in 0 until ma.length()) meds.add(medFrom(ma.getJSONObject(i)))
            val la = o.optJSONArray("logs") ?: JSONArray()
            for (i in 0 until la.length()) {
                val l = la.getJSONObject(i)
                logs.add(DoseLog(l.getString("m"), l.getString("n"), l.getLong("s"), l.getLong("t"), l.getString("a")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun persist() {
        val ma = JSONArray()
        meds.forEach { ma.put(medTo(it)) }
        val la = JSONArray()
        logs.takeLast(3000).forEach {
            la.put(JSONObject().put("m", it.medId).put("n", it.medName).put("s", it.scheduled).put("t", it.time).put("a", it.action))
        }
        val o = JSONObject().put("meds", ma).put("logs", la)
        file.writeText(o.toString())
    }

    private fun medTo(m: Medication): JSONObject {
        val times = JSONArray()
        m.times.forEach { times.put(it.hour * 60 + it.minute) }
        val days = JSONArray()
        m.weekdays.forEach { days.put(it) }
        return JSONObject()
            .put("id", m.id).put("name", m.name).put("dose", m.dose).put("note", m.note)
            .put("times", times).put("days", days)
            .put("stock", m.stock ?: -1).put("low", m.lowStock)
            .put("photo", m.photo ?: "").put("active", m.active)
    }

    private fun medFrom(o: JSONObject): Medication {
        val t = o.getJSONArray("times")
        val d = o.getJSONArray("days")
        return Medication(
            id = o.getString("id"),
            name = o.optString("name"),
            dose = o.optString("dose"),
            note = o.optString("note"),
            times = (0 until t.length()).map { DoseTime(t.getInt(it) / 60, t.getInt(it) % 60) },
            weekdays = (0 until d.length()).map { d.getInt(it) }.toSet(),
            stock = o.optInt("stock", -1).takeIf { it >= 0 },
            lowStock = o.optInt("low", 5),
            photo = o.optString("photo").ifEmpty { null },
            active = o.optBoolean("active", true),
        )
    }

    fun med(id: String): Medication? = meds.firstOrNull { it.id == id }

    // ---- ფოტოები

    fun loadPhoto(m: Medication): Bitmap? {
        val p = m.photo ?: return null
        return try { BitmapFactory.decodeFile(File(dir, p).path) } catch (e: Exception) { null }
    }

    fun decode(ctx: Context, uri: Uri): Bitmap? = try {
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.fit(1200)
    } catch (e: Exception) { null }

    // ---- შენახვა / წაშლა

    fun save(ctx: Context, med: Medication, bitmap: Bitmap?, photoChanged: Boolean) {
        var m = med
        val old = med(m.id)
        if (photoChanged) {
            old?.photo?.let { File(dir, it).delete() }
            m = m.copy(photo = null)
            if (bitmap != null) {
                val name = "photo-${UUID.randomUUID()}.jpg"
                File(dir, name).outputStream().use { bitmap.fit(900).compress(Bitmap.CompressFormat.JPEG, 85, it) }
                m = m.copy(photo = name)
            }
        }
        if (old != null) Alarms.cancel(ctx, old)
        val i = meds.indexOfFirst { it.id == m.id }
        if (i >= 0) meds[i] = m else meds.add(m)
        persist()
        Alarms.schedule(ctx, m)
    }

    fun delete(ctx: Context, m: Medication) {
        Alarms.cancel(ctx, m)
        m.photo?.let { File(dir, it).delete() }
        meds.removeAll { it.id == m.id }
        persist()
    }

    // ---- ჟურნალი

    fun status(medId: String, scheduled: Long): String? =
        logs.lastOrNull { it.medId == medId && abs(it.scheduled - scheduled) < 60_000 && it.action != SNOOZED }?.action

    fun record(action: String, medId: String, scheduled: Long) {
        val i = meds.indexOfFirst { it.id == medId }
        if (i < 0) return
        if (action != SNOOZED && status(medId, scheduled) != null) return
        logs.add(DoseLog(medId, meds[i].name, scheduled, System.currentTimeMillis(), action))
        if (action == TAKEN) {
            meds[i].stock?.let { meds[i] = meds[i].copy(stock = maxOf(0, it - 1)) }
        }
        persist()
    }

    fun undo(medId: String, scheduled: Long) {
        val idx = logs.indexOfLast { it.medId == medId && abs(it.scheduled - scheduled) < 60_000 && it.action != SNOOZED }
        if (idx < 0) return
        if (logs[idx].action == TAKEN) {
            val i = meds.indexOfFirst { it.id == medId }
            if (i >= 0) meds[i].stock?.let { meds[i] = meds[i].copy(stock = it + 1) }
        }
        logs.removeAt(idx)
        persist()
    }

    // ---- გრაფიკი და სტატისტიკა

    fun slots(day: Long): List<DoseSlot> {
        val base = Calendar.getInstance().apply { timeInMillis = day }
        val wd = base.get(Calendar.DAY_OF_WEEK)
        return meds.filter { it.active && wd in it.weekdays }.flatMap { m ->
            m.times.map { t ->
                val c = (base.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, t.hour); set(Calendar.MINUTE, t.minute)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                DoseSlot(m, t, c.timeInMillis)
            }
        }.sortedBy { it.at }
    }

    val lowStock: List<Medication>
        get() = meds.filter { m -> m.active && (m.stock?.let { it <= m.lowStock } ?: false) }

    fun adherence(days: Int = 7): Pair<Int, Int> {
        var taken = 0
        var due = 0
        val now = System.currentTimeMillis()
        for (offset in 0 until days) {
            val day = now - offset * 86_400_000L
            for (s in slots(day)) {
                if (s.at >= now) continue
                due++
                if (status(s.med.id, s.at) == TAKEN) taken++
            }
        }
        return taken to due
    }
}

fun Bitmap.fit(max: Int): Bitmap {
    val s = minOf(1f, max.toFloat() / maxOf(width, height))
    return if (s >= 1f) this else Bitmap.createScaledBitmap(this, (width * s).toInt(), (height * s).toInt(), true)
}
