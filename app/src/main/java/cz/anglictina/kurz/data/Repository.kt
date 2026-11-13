package cz.anglictina.kurz.data

import android.content.Context
import cz.anglictina.kurz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var userName: String get() = p.getString("name", "") ?: ""; set(v) = p.edit().putString("name", v).apply()
    var minutes: Int get() = p.getInt("minutes", 15); set(v) = p.edit().putInt("minutes", v).apply()
    var reminderOn: Boolean get() = p.getBoolean("rem", true); set(v) = p.edit().putBoolean("rem", v).apply()
    var reminderHour: Int get() = p.getInt("remH", 19); set(v) = p.edit().putInt("remH", v).apply()
    var reminderMinute: Int get() = p.getInt("remM", 0); set(v) = p.edit().putInt("remM", v).apply()
    var bankNewPerDay: Int get() = p.getInt("bankNew", 15); set(v) = p.edit().putInt("bankNew", v).apply()
    var bankLevels: Set<String> get() = p.getStringSet("bankLevels", setOf("B1", "B2")) ?: setOf("B1", "B2"); set(v) = p.edit().putStringSet("bankLevels", v).apply()
    var bankPacks: Set<String> get() = p.getStringSet("bankPacks", emptySet()) ?: emptySet(); set(v) = p.edit().putStringSet("bankPacks", v).apply()
    var direction: String get() = p.getString("dir", "mix") ?: "mix"; set(v) = p.edit().putString("dir", v).apply()   // en2cz | cz2en | mix
    var speakAuto: Boolean get() = p.getBoolean("speak", true); set(v) = p.edit().putBoolean("speak", v).apply()
    var dataUrl: String get() = p.getString("url", BuildConfig.DATA_URL) ?: BuildConfig.DATA_URL; set(v) = p.edit().putString("url", v).apply()
    var lastSync: Long get() = p.getLong("lastSync", 0); set(v) = p.edit().putLong("lastSync", v).apply()
}

/** Pokrok uživatele – kartičky a statistika dnů. Uloženo v progress.json. */
class Progress(private val file: File) {
    val cards = HashMap<String, CardState>()
    val daySeconds = HashMap<String, Int>()      // yyyy-MM-dd -> sekundy učení
    val dayItems = HashMap<String, Int>()        // yyyy-MM-dd -> počet odpovědí
    val bankNewByDay = HashMap<String, Int>()    // yyyy-MM-dd -> nově zavedená slova ze zásoby

    init { load() }

    fun card(id: String) = cards.getOrPut(id) { CardState() }
    fun peek(id: String): CardState? = cards[id]

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            o.optJSONObject("cards")?.let { c -> c.keys().forEach { cards[it] = CardState.fromJson(c.getJSONObject(it)) } }
            fun m(name: String, into: HashMap<String, Int>) = o.optJSONObject(name)?.let { c -> c.keys().forEach { into[it] = c.getInt(it) } }
            m("days", daySeconds); m("items", dayItems); m("bankNew", bankNewByDay)
        }
    }

    @Synchronized fun save() {
        val o = JSONObject()
        o.put("cards", JSONObject().apply { cards.forEach { (k, v) -> put(k, v.toJson()) } })
        o.put("days", JSONObject(daySeconds as Map<*, *>))
        o.put("items", JSONObject(dayItems as Map<*, *>))
        o.put("bankNew", JSONObject(bankNewByDay as Map<*, *>))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString()); tmp.renameTo(file)
    }

    fun addTime(seconds: Int, items: Int) {
        val d = today()
        daySeconds[d] = (daySeconds[d] ?: 0) + seconds
        dayItems[d] = (dayItems[d] ?: 0) + items
    }

    /** Počet dní v řadě s aspoň 3 minutami učení (dnešek se počítá, pokud už proběhl). */
    fun streak(): Int {
        var n = 0
        val c = java.util.Calendar.getInstance()
        if ((daySeconds[fmt(c.time)] ?: 0) < 180) c.add(java.util.Calendar.DAY_OF_YEAR, -1)
        while ((daySeconds[fmt(c.time)] ?: 0) >= 180) { n++; c.add(java.util.Calendar.DAY_OF_YEAR, -1) }
        return n
    }

    companion object {
        private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun fmt(d: Date): String = df.format(d)
        fun today(): String = fmt(Date())
    }
}

class Repository(private val ctx: Context) {
    val settings = Settings(ctx)
    val progress = Progress(File(ctx.filesDir, "progress.json"))
    var lessons: List<Lesson> = emptyList(); private set
    var packs: List<BankPack> = emptyList(); private set
    var practice: List<PracticePack> = emptyList(); private set

    private val dataDir = File(ctx.filesDir, "data").apply { mkdirs() }

    private fun readData(path: String): String? {
        val f = File(dataDir, path)
        if (f.exists()) return f.readText()
        return runCatching { ctx.assets.open("data/$path").bufferedReader().use { it.readText() } }.getOrNull()
    }

    fun load() {
        val idx = readData("index.json")?.let { JSONObject(it) } ?: return
        val ls = mutableListOf<Lesson>()
        idx.optJSONArray("lessons")?.let { a ->
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                readData(e.getString("file"))?.let { t -> runCatching { ls += parseLesson(JSONObject(t)) } }
            }
        }
        val ps = mutableListOf<BankPack>()
        idx.optJSONArray("bank")?.let { a ->
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                readData(e.getString("file"))?.let { t -> runCatching { ps += parsePack(JSONObject(t)) } }
            }
        }
        val pr = mutableListOf<PracticePack>()
        idx.optJSONArray("practice")?.let { a ->
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                readData(e.getString("file"))?.let { t -> runCatching { pr += parsePractice(JSONObject(t)) } }
            }
        }
        lessons = ls.sortedBy { it.week }
        packs = ps
        practice = pr
    }

    fun absoluteUrl(rel: String): String = if (rel.startsWith("http")) rel else settings.dataUrl.trimEnd('/') + "/" + rel

    private fun fetch(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    /** Stáhne nový obsah. Vrací počet nově stažených souborů. */
    suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val base = settings.dataUrl.trimEnd('/') + "/"
            val remote = JSONObject(String(fetch(base + "index.json")))
            val localIdx = readData("index.json")?.let { JSONObject(it) }
            val localVersions = HashMap<String, Int>()
            for (key in listOf("lessons", "bank", "practice")) localIdx?.optJSONArray(key)?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { localVersions[it.getString("file")] = it.optInt("v", 1) }
            }
            var n = 0
            for (key in listOf("lessons", "bank", "practice")) remote.optJSONArray(key)?.let { a ->
                for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    val file = e.getString("file")
                    val have = File(dataDir, file).exists() || localVersions.containsKey(file)
                    if (!have || (localVersions[file] ?: 0) < e.optInt("v", 1)) {
                        val bytes = fetch(base + file)
                        JSONObject(String(bytes)) // validace
                        File(dataDir, file).apply { parentFile?.mkdirs() }.writeBytes(bytes)
                        n++
                    }
                }
            }
            File(dataDir, "index.json").writeText(remote.toString())
            settings.lastSync = System.currentTimeMillis()
            load()
            n
        }
    }

    fun allLessonWords() = lessons.flatMap { it.words }
    fun allBankWords() = packs.flatMap { it.words }
}
