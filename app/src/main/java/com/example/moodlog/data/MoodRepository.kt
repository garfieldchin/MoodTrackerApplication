package com.example.moodlog.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import androidx.compose.runtime.mutableStateListOf
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalToday

private const val TABLE = "mood_records"
private const val T_EVENTS = "life_events"
private const val T_DIMS = "life_dims"
private const val T_TAGS = "custom_tags"
private const val T_HIDDEN_TAGS = "hidden_tags"
private const val T_EMOTION_TAGS = "emotion_tags"
private const val T_STANCE = "stances"
private const val T_CONTACT = "contacts"
private const val T_ENCOUNTER = "encounters"
private const val T_MEAL = "meals"
private const val T_WEIGHT = "weights"
private const val T_MOVE = "moves"
private const val T_MOVE_TAG = "move_tags"
private const val DB_VERSION = 10

class MoodDbHelper(context: Context) : SQLiteOpenHelper(context, "moodlog.db", null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                miss INTEGER NOT NULL,
                panic INTEGER NOT NULL,
                contact INTEGER NOT NULL,
                confirm INTEGER NOT NULL,
                diary TEXT NOT NULL DEFAULT '',
                e_longing INTEGER,
                e_abandoned INTEGER,
                e_anger INTEGER,
                e_shame INTEGER,
                e_fear INTEGER,
                e_loneliness INTEGER,
                e_resentment INTEGER,
                attachments TEXT NOT NULL DEFAULT '',
                impulse_level INTEGER,
                outcome INTEGER NOT NULL DEFAULT 0,
                episode_at INTEGER,
                outcome_at INTEGER,
                safe_at INTEGER,
                custom_emotions TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_EVENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                tags TEXT NOT NULL DEFAULT '',
                energy_before INTEGER NOT NULL DEFAULT 0,
                energy_after INTEGER NOT NULL DEFAULT 0,
                note TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_DIMS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                agency INTEGER NOT NULL DEFAULT 0,
                achievement INTEGER NOT NULL DEFAULT 0,
                vitality INTEGER NOT NULL DEFAULT 0,
                connection INTEGER NOT NULL DEFAULT 0,
                value INTEGER NOT NULL DEFAULT 0,
                joy INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_TAGS (
                name TEXT PRIMARY KEY,
                used_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // 被删掉的标签记在这里 —— 预设的那几个也能删，所以要分开记。
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_HIDDEN_TAGS (
                name TEXT PRIMARY KEY
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_EMOTION_TAGS (
                name TEXT PRIMARY KEY,
                sort INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // 「我做主」从「一天一条」改成「一次一条」，所以 date 不再唯一。
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_STANCE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                keys TEXT NOT NULL DEFAULT '',
                custom TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                spoken INTEGER,
                ts INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_CONTACT (
                slot INTEGER PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                phone TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_ENCOUNTER (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                happened TEXT NOT NULL DEFAULT '',
                feeling TEXT NOT NULL DEFAULT '',
                need TEXT NOT NULL DEFAULT '',
                todo TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_MEAL (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                kind TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                medicine INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_WEIGHT (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                kg REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_MOVE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                tag TEXT NOT NULL DEFAULT '',
                minutes INTEGER NOT NULL DEFAULT 0,
                note TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_MOVE_TAG (
                name TEXT PRIMARY KEY,
                used_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN e_longing INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN e_abandoned INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN e_anger INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN e_shame INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN e_fear INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN e_loneliness INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN attachments TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN e_resentment INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN impulse_level INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN outcome INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN outcome_at INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN safe_at INTEGER")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN episode_at INTEGER")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN custom_emotions TEXT NOT NULL DEFAULT ''")
            // 老记录里 outcome_at 存的正是这一波开始的那一刻，把它挪到新字段上，语义才对得上。
            db.execSQL("UPDATE $TABLE SET episode_at = COALESCE(episode_at, outcome_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $T_EMOTION_TAGS (
                    name TEXT PRIMARY KEY,
                    sort INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            // date UNIQUE 没法直接删，重建一张：同一天可以有很多条了。
            db.execSQL("ALTER TABLE $T_STANCE RENAME TO ${T_STANCE}_v3")
            db.execSQL(
                """
                CREATE TABLE $T_STANCE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    date TEXT NOT NULL,
                    keys TEXT NOT NULL DEFAULT '',
                    custom TEXT NOT NULL DEFAULT '',
                    note TEXT NOT NULL DEFAULT '',
                    spoken INTEGER,
                    ts INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO $T_STANCE (id, date, keys, custom, note, spoken)
                SELECT id, date, keys, custom, note, spoken FROM ${T_STANCE}_v3
                """.trimIndent()
            )
            db.execSQL("DROP TABLE ${T_STANCE}_v3")
        }
        if (oldVersion < 5) {
            // 「我做主」本来只存日期，时间轴要按时刻排，所以补一列。
            // 老记录没有时刻，留 0 —— 界面上退化成「第 N 次」，不去编一个假时间。
            db.execSQL("ALTER TABLE $T_STANCE ADD COLUMN ts INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 6) {
            // 标签可以删了。删掉的（包括预设的那几个）名字记在这张表里。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $T_HIDDEN_TAGS (
                    name TEXT PRIMARY KEY
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 7) {
            // 能量的量轴从 50 改成 0。
            // 老数据里 energy_before 恒为 5（也就是 50 那根起点线）、energy_after 是 5 + 差值。
            // 差值本身没错，错的是那根线 —— 所以把线抹平到 0，差值原样留下：
            // 记过「+3」的那件事，改完之后还是 +3，只是它现在从 0 往上走了。
            db.execSQL("UPDATE $T_EVENTS SET energy_after = energy_after - energy_before")
            db.execSQL("UPDATE $T_EVENTS SET energy_before = 0")
        }
        if (oldVersion < 8) {
            // 【我遇见】：四个问题的四句话，和【我话事】一样按次记、按逻辑日归。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $T_ENCOUNTER (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    happened TEXT NOT NULL DEFAULT '',
                    feeling TEXT NOT NULL DEFAULT '',
                    need TEXT NOT NULL DEFAULT '',
                    todo TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 9) {
            // 【我身体】：吃和体重。两张表都是新增，老数据一条不动。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $T_MEAL (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    kind TEXT NOT NULL DEFAULT '',
                    content TEXT NOT NULL DEFAULT '',
                    medicine INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $T_WEIGHT (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    kg REAL NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 10) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $T_MOVE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    tag TEXT NOT NULL DEFAULT '',
                    minutes INTEGER NOT NULL DEFAULT 0,
                    note TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $T_MOVE_TAG (
                    name TEXT PRIMARY KEY,
                    used_count INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }
}

/**
 * 本地数据仓库。数据只保存在手机本地数据库中，不上传。
 * 所有列表都是可观察的，写入后自动刷新，界面随之更新。
 */
class MoodRepository(context: Context) {

    private val helper = MoodDbHelper(context.applicationContext)
    private val appContext = context.applicationContext

    /** 数据库文件路径（备份时读它）。 */
    fun getDbFile(): File = appContext.getDatabasePath("moodlog.db")

    /** 把 WAL 里没落盘的数据合并回主库文件，备份前调用，保证拷出来的是完整数据。 */
    fun flushWal() {
        runCatching { helper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)") }
    }

    /** 关闭底层连接。恢复备份前必须调用，否则打开的数据库连接会读到旧文件。 */
    fun closeDb() = helper.close()

    private val _records = mutableStateListOf<MoodRecord>()
    val records: List<MoodRecord> get() = _records

    private val _events = mutableStateListOf<LifeEvent>()
    val events: List<LifeEvent> get() = _events

    private val _encounters = mutableStateListOf<Encounter>()
    val encounters: List<Encounter> get() = _encounters

    private val _dims = mutableStateListOf<LifeDims>()
    val dims: List<LifeDims> get() = _dims

    private val _stances = mutableStateListOf<Stance>()
    val stances: List<Stance> get() = _stances

    private val _tags = mutableStateListOf<CustomTag>()
    val tags: List<CustomTag> get() = _tags

    /** 被删掉的标签名字。预设的那几个也在里面 —— 删掉不等于删数据，只是不再出现在那一排。 */
    private val _hiddenTags = mutableStateListOf<String>()
    val hiddenTags: List<String> get() = _hiddenTags

    private val _emotionTags = mutableStateListOf<String>()
    val emotionTags: List<String> get() = _emotionTags

    private val _contacts = mutableStateListOf<Contact>()
    val contacts: List<Contact> get() = _contacts

    private val _meals = mutableStateListOf<Meal>()
    val meals: List<Meal> get() = _meals

    private val _weights = mutableStateListOf<Weight>()
    val weights: List<Weight> get() = _weights

    private val _moves = mutableStateListOf<Move>()
    val moves: List<Move> get() = _moves

    /** 用户自己加过的运动类型，用得多的排前面。 */
    private val _moveTags = mutableStateListOf<CustomTag>()
    val moveTags: List<CustomTag> get() = _moveTags

    init {
        refresh()
    }

    fun refresh() {
        _records.clear(); _records.addAll(queryRecords())
        _events.clear(); _events.addAll(queryEvents())
        _encounters.clear(); _encounters.addAll(queryEncounters())
        _dims.clear(); _dims.addAll(queryDims())
        _stances.clear(); _stances.addAll(queryStances())
        _tags.clear(); _tags.addAll(queryTags())
        _hiddenTags.clear(); _hiddenTags.addAll(queryHiddenTags())
        _emotionTags.clear(); _emotionTags.addAll(queryEmotionTags())
        _contacts.clear(); _contacts.addAll(queryContacts())
        _meals.clear(); _meals.addAll(queryMeals())
        _weights.clear(); _weights.addAll(queryWeights())
        _moves.clear(); _moves.addAll(queryMoves())
        _moveTags.clear(); _moveTags.addAll(queryMoveTags())
    }

    // ---------- 我身体 ----------

    fun insertMeal(m: Meal) {
        helper.writableDatabase.insert(
            T_MEAL, null,
            ContentValues().apply {
                put("timestamp", m.timestamp)
                put("kind", m.kind)
                put("content", m.content)
                put("medicine", if (m.medicine) 1 else 0)
            }
        )
        refresh()
    }

    fun updateMeal(m: Meal) {
        helper.writableDatabase.update(
            T_MEAL,
            ContentValues().apply {
                put("timestamp", m.timestamp)
                put("kind", m.kind)
                put("content", m.content)
                put("medicine", if (m.medicine) 1 else 0)
            },
            "id = ?", arrayOf(m.id.toString())
        )
        refresh()
    }

    fun deleteMeal(id: Long) {
        helper.writableDatabase.delete(T_MEAL, "id = ?", arrayOf(id.toString()))
        refresh()
    }

    fun insertMove(m: Move) {
        helper.writableDatabase.insert(
            T_MOVE, null,
            ContentValues().apply {
                put("timestamp", m.timestamp)
                put("tag", m.tag)
                put("minutes", m.minutes)
                put("note", m.note)
            }
        )
        bumpMoveTag(m.tag)
        refresh()
    }

    /** timestamp 一定要写进去：不然拖了时间保存，看着没生效的样子。 */
    fun updateMove(m: Move) {
        helper.writableDatabase.update(
            T_MOVE,
            ContentValues().apply {
                put("timestamp", m.timestamp)
                put("tag", m.tag)
                put("minutes", m.minutes)
                put("note", m.note)
            },
            "id = ?", arrayOf(m.id.toString())
        )
        refresh()
    }

    fun deleteMove(id: Long) {
        helper.writableDatabase.delete(T_MOVE, "id = ?", arrayOf(id.toString()))
        refresh()
    }

    fun addMoveTag(name: String) {
        if (name.isBlank()) return
        helper.writableDatabase.insertWithOnConflict(
            T_MOVE_TAG, null,
            ContentValues().apply { put("name", name.trim()); put("used_count", 0) },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        refresh()
    }

    private fun bumpMoveTag(name: String) {
        if (name.isBlank()) return
        helper.writableDatabase.insertWithOnConflict(
            T_MOVE_TAG, null,
            ContentValues().apply { put("name", name); put("used_count", 1) },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        helper.writableDatabase.execSQL(
            "UPDATE $T_MOVE_TAG SET used_count = used_count + 1 WHERE name = ?",
            arrayOf(name)
        )
    }

    fun insertWeight(w: Weight) {
        helper.writableDatabase.insert(
            T_WEIGHT, null,
            ContentValues().apply { put("timestamp", w.timestamp); put("kg", w.kg) }
        )
        refresh()
    }

    fun deleteWeight(id: Long) {
        helper.writableDatabase.delete(T_WEIGHT, "id = ?", arrayOf(id.toString()))
        refresh()
    }

    fun updateWeight(w: Weight) {
        helper.writableDatabase.update(
            T_WEIGHT,
            ContentValues().apply { put("timestamp", w.timestamp); put("kg", w.kg) },
            "id = ?", arrayOf(w.id.toString())
        )
        refresh()
    }

    /**
     * 记一次体重：同一天（逻辑日）只留最新那次。
     *
     * 不是删掉你的历史 —— 是把「今天的体重」收敛成一个数字。早上称了晚上又称，
     * 你要看的是晚上那一个数，它才说得上今天是多重；留在一条列表里的两个红点数
     * 只会让曲线在同一个 x 上打结。
     *
     * 旧版本允许一天记多次，库里可能躺着同一天的几条。这里一并收拢，
     * 保留时刻最晚的那条作为当天的那条，其余删掉 —— 老数据不用你自己去清。
     *
     * 时刻取按「记下」的那一刻，而不是渲染这一页的时刻：
     * 页面放在后台半小时再称一次，时间也还是准的。
     */
    fun recordWeight(kg: Double) {
        val now = System.currentTimeMillis()
        val day = now.logicalDate()
        val same = _weights.filter { it.timestamp.logicalDate() == day }.sortedBy { it.timestamp }
        val db = helper.writableDatabase
        if (same.isEmpty()) {
            db.insert(
                T_WEIGHT, null,
                ContentValues().apply { put("timestamp", now); put("kg", kg) }
            )
        } else {
            val keep = same.last()
            db.update(
                T_WEIGHT,
                ContentValues().apply { put("timestamp", now); put("kg", kg) },
                "id = ?", arrayOf(keep.id.toString())
            )
            same.filter { it.id != keep.id }.forEach {
                db.delete(T_WEIGHT, "id = ?", arrayOf(it.id.toString()))
            }
        }
        refresh()
    }

    // ---------- A 空间 ----------

    fun insert(record: MoodRecord): Long {
        val id = helper.writableDatabase.insert(TABLE, null, toValues(record))
        refresh()
        return id
    }

    fun update(record: MoodRecord) {
        if (record.id == 0L) return
        helper.writableDatabase.update(TABLE, toValues(record), "id = ?", arrayOf(record.id.toString()))
        refresh()
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
        refresh()
    }

    /**
     * 「后来怎么样了」。它永远发生在这次记录之后 —— 可以现在答，也可以当天回来补。
     * episodeAt（这一波什么时候开始的）不动，它是在填写时就定下的。
     */
    fun markOutcome(id: Long, outcome: Int, at: Long = System.currentTimeMillis()) {
        if (id == 0L) return
        val v = ContentValues().apply {
            put("outcome", outcome)
            put("outcome_at", at)
        }
        helper.writableDatabase.update(TABLE, v, "id = ?", arrayOf(id.toString()))
        refresh()
    }

    fun markSafe(id: Long, at: Long = System.currentTimeMillis()) {
        if (id == 0L) return
        val v = ContentValues().apply { put("safe_at", at) }
        helper.writableDatabase.update(TABLE, v, "id = ?", arrayOf(id.toString()))
        refresh()
    }

    fun addEmotionTag(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        helper.writableDatabase.insertWithOnConflict(
            T_EMOTION_TAGS, null,
            ContentValues().apply {
                put("name", n)
                put("sort", (queryEmotionTags().size))
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        refresh()
    }

    fun deleteEmotionTag(name: String) {
        helper.writableDatabase.delete(T_EMOTION_TAGS, "name = ?", arrayOf(name))
        refresh()
    }

    // ---------- B 空间 ----------

    fun insertEvent(e: LifeEvent): Long {
        val v = ContentValues().apply {
            put("timestamp", e.timestamp)
            put("tags", e.tags.joinToString("\n"))
            put("energy_before", e.energyBefore)
            put("energy_after", e.energyAfter)
            put("note", e.note)
        }
        val id = helper.writableDatabase.insert(T_EVENTS, null, v)
        e.tags.filter { it !in PRESET_TAGS }.forEach { bumpTag(it) }
        refresh()
        return id
    }

    fun deleteEvent(id: Long) {
        helper.writableDatabase.delete(T_EVENTS, "id = ?", arrayOf(id.toString()))
        refresh()
    }

    /**
     * 改一条已经记下的事：改的是「当时怎么写的」，不是这件事本身。
     *
     * 标签那一行是纯文本进出的 —— 界面上用什么分隔都行，进库前在这里拆开，
     * 一个词占一行，和插入时的格式保持一致。
     */
    fun updateEvent(e: LifeEvent) {
        if (e.id == 0L) return
        helper.writableDatabase.update(T_EVENTS, ContentValues().apply {
            put("timestamp", e.timestamp)
            put("tags", e.tags.joinToString("\n"))
            put("energy_before", e.energyBefore)
            put("energy_after", e.energyAfter)
            put("note", e.note)
        }, "id = ?", arrayOf(e.id.toString()))
        refresh()
    }

    // ---------- 我遇见 ----------

    fun insertEncounter(e: Encounter): Long {
        val id = helper.writableDatabase.insert(T_ENCOUNTER, null, ContentValues().apply {
            put("timestamp", e.timestamp)
            put("happened", e.happened.trim())
            put("feeling", e.feeling.trim())
            put("need", e.need.trim())
            put("todo", e.doNext.trim())
        })
        refresh()
        return id
    }

    fun updateEncounter(e: Encounter) {
        if (e.id == 0L) return
        helper.writableDatabase.update(T_ENCOUNTER, ContentValues().apply {
            put("timestamp", e.timestamp)
            put("happened", e.happened.trim())
            put("feeling", e.feeling.trim())
            put("need", e.need.trim())
            put("todo", e.doNext.trim())
        }, "id = ?", arrayOf(e.id.toString()))
        refresh()
    }

    fun deleteEncounter(id: Long) {
        helper.writableDatabase.delete(T_ENCOUNTER, "id = ?", arrayOf(id.toString()))
        refresh()
    }

    /** 一天只留一份答案：再保存就是覆盖，不会把这一天的平均值拉歪。 */
    fun upsertDims(d: LifeDims) {
        val day = d.timestamp.logicalDate()
        val db = helper.writableDatabase
        val (where, args) = dayRangeWhere(day)
        db.delete(T_DIMS, where, args)
        val v = ContentValues().apply {
            put("timestamp", d.timestamp)
            put("agency", d.agency)
            put("achievement", d.achievement)
            put("vitality", d.vitality)
            put("connection", d.connection)
            put("value", d.value)
            put("joy", d.joy)
        }
        db.insert(T_DIMS, null, v)
        refresh()
    }

    fun dimsOf(day: java.time.LocalDate): LifeDims? =
        _dims.firstOrNull { it.timestamp.logicalDate() == day }

    fun addTag(name: String) {
        if (name.isBlank()) return
        val trimmed = name.trim()
        helper.writableDatabase.insertWithOnConflict(
            T_TAGS, null,
            ContentValues().apply { put("name", trimmed); put("used_count", 0) },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        // 加回来的时候，把它从「已删除」里放出来 —— 不然看着像加上了其实没出现。
        helper.writableDatabase.delete(T_HIDDEN_TAGS, "name = ?", arrayOf(trimmed))
        refresh()
    }

    private fun bumpTag(name: String) {
        helper.writableDatabase.execSQL(
            "UPDATE $T_TAGS SET used_count = used_count + 1 WHERE name = ?",
            arrayOf(name)
        )
    }

    /**
     * 删一个标签。
     * 只把它从那一排里拿掉，**不动任何已经记下来的事** ——
     * 过去那件写着「运动」的记录，不会因为你现在不爱用这个词就改口。
     */
    fun deleteTag(name: String) {
        helper.writableDatabase.delete(T_TAGS, "name = ?", arrayOf(name))
        helper.writableDatabase.insertWithOnConflict(
            T_HIDDEN_TAGS, null,
            ContentValues().apply { put("name", name) },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        refresh()
    }

    /** 把预设的六个放回那一排。自己加过的标签不会跟着回来 —— 那些要重新加。 */
    fun restorePresetTags() {
        helper.writableDatabase.delete(T_HIDDEN_TAGS, null, null)
        refresh()
    }

    /**
     * 「我做主」一次一条。同一天可以记很多次 —— 早上守住了、中午没守住、晚上又拉回来一次，
     * 三条都在，不会被后一条覆盖。
     */
    fun insertStance(s: Stance): Long {
        val v = ContentValues().apply {
            put("date", s.date)
            put("keys", s.keys.joinToString("\n"))
            put("custom", s.custom)
            put("note", s.note)
            if (s.spoken == null) putNull("spoken") else put("spoken", s.spoken)
            put("ts", if (s.ts > 0L) s.ts else System.currentTimeMillis())
        }
        val id = helper.writableDatabase.insert(T_STANCE, null, v)
        refresh()
        return id
    }

    fun deleteStance(id: Long) {
        helper.writableDatabase.delete(T_STANCE, "id = ?", arrayOf(id.toString()))
        refresh()
    }

    /** 给刚记下的那一条补一句「尽管…」。打卡是按次的，备注也得能按次改。 */
    fun updateStanceNote(id: Long, note: String) {
        helper.writableDatabase.update(
            T_STANCE,
            ContentValues().apply { put("note", note.trim()) },
            "id = ?",
            arrayOf(id.toString())
        )
        refresh()
    }

    fun stancesOf(date: String): List<Stance> =
        _stances.filter { it.date == date }

    /**
     * 紧急联系人。三个位子，写死在库里（slot 就是主键）。
     * 号码只存在这台手机上，不上传、不同步 —— 它只在安全底座第 3 步被读出来拨号。
     */
    fun upsertContact(c: Contact) {
        val v = ContentValues().apply {
            put("slot", c.slot)
            put("name", c.name)
            put("phone", c.phone)
        }
        helper.writableDatabase.insertWithOnConflict(
            T_CONTACT, null, v, SQLiteDatabase.CONFLICT_REPLACE
        )
        refresh()
    }

    fun deleteContact(slot: Int) {
        helper.writableDatabase.delete(T_CONTACT, "slot = ?", arrayOf(slot.toString()))
        refresh()
    }

    // ---------- 查询 ----------

    /**
     * 某个逻辑日的时间区间：[day 04:00, day+1 04:00)。
     * 用毫秒比大小，不用任何 SQLite 的日期函数 —— 时区、夏令时都躲开了。
     */
    private fun dayRangeWhere(day: java.time.LocalDate): Pair<String, Array<String>> {
        // 逻辑日 = [(day 04:00), (day+1 04:00))
        val zone = java.time.ZoneId.systemDefault()
        val start = day.atTime(DAY_START_HOUR_FOR_SQL, 0).atZone(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atTime(DAY_START_HOUR_FOR_SQL, 0).atZone(zone).toInstant().toEpochMilli()
        return "timestamp >= ? AND timestamp < ?" to arrayOf(start.toString(), end.toString())
    }

    private fun toValues(r: MoodRecord): ContentValues = ContentValues().apply {
        put("timestamp", r.timestamp)
        put("miss", r.miss)
        put("panic", r.panic)
        put("contact", r.contact)
        put("confirm", r.confirm)
        put("diary", r.diary)
        val e = r.emotions
        putNullableInt("e_abandoned", e?.abandoned)
        putNullableInt("e_anger", e?.anger)
        putNullableInt("e_shame", e?.shame)
        putNullableInt("e_fear", e?.fear)
        putNullableInt("e_loneliness", e?.loneliness)
        putNullableInt("e_resentment", e?.resentment)
        put("attachments", r.attachments.joinToString("\n"))
        putNullableInt("impulse_level", r.impulse)
        put("outcome", r.outcome)
        putNullableLong("episode_at", r.episodeAt)
        putNullableLong("outcome_at", r.outcomeAt)
        putNullableLong("safe_at", r.safeAt)
        // 自己加的那几种：填了的才写（0 分也是一个答案，别把它滤掉），没填的一个字都不留。
        put(
            "custom_emotions",
            r.customEmotions.joinToString("\n") { "${it.first}=${it.second}" }
        )
    }

    private fun ContentValues.putNullableInt(key: String, value: Int?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullableLong(key: String, value: Long?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun queryRecords(): List<MoodRecord> {
        val result = mutableListOf<MoodRecord>()
        helper.readableDatabase.query(TABLE, null, null, null, null, null, "timestamp DESC")
            .use { c ->
                while (c.moveToNext()) {
                    val emotions = listOf(
                        c.intOrNull("e_abandoned"),
                        c.intOrNull("e_anger"),
                        c.intOrNull("e_shame"),
                        c.intOrNull("e_fear"),
                        c.intOrNull("e_loneliness"),
                        c.intOrNull("e_resentment")
                    )
                    // 六列里只要填过一支，这一组就算存在（剩下的仍是 null = 没答）。
                    val hasEmotions = emotions.any { it != null }
                    result.add(
                        MoodRecord(
                            id = c.long("id"),
                            timestamp = c.long("timestamp"),
                            miss = c.int("miss"),
                            panic = c.int("panic"),
                            contact = c.int("contact"),
                            confirm = c.int("confirm"),
                            diary = c.string("diary"),
                            emotions = if (hasEmotions) {
                                EmotionSet(
                                    abandoned = emotions[0],
                                    anger = emotions[1],
                                    shame = emotions[2],
                                    fear = emotions[3],
                                    loneliness = emotions[4],
                                    resentment = emotions[5]
                                )
                            } else null,
                            customEmotions = parseCustomEmotions(c.string("custom_emotions")),
                            attachments = c.string("attachments").lines().filter { it.isNotBlank() },
                            impulse = c.intOrNull("impulse_level"),
                            outcome = c.intOrNull("outcome") ?: 0,
                            episodeAt = c.longOrNull("episode_at"),
                            outcomeAt = c.longOrNull("outcome_at"),
                            safeAt = c.longOrNull("safe_at")
                        )
                    )
                }
            }
        return result
    }

    private fun parseCustomEmotions(raw: String): List<Pair<String, Int>> =
        raw.lineSequence()
            .mapNotNull { line ->
                val idx = line.lastIndexOf('=')
                if (idx <= 0) null
                else {
                    val name = line.substring(0, idx)
                    val value = line.substring(idx + 1).toIntOrNull() ?: return@mapNotNull null
                    name to value
                }
            }
            .toList()

    private fun queryEvents(): List<LifeEvent> {
        val result = mutableListOf<LifeEvent>()
        helper.readableDatabase.query(T_EVENTS, null, null, null, null, null, "timestamp DESC")
            .use { c ->
                while (c.moveToNext()) {
                    result.add(
                        LifeEvent(
                            id = c.long("id"),
                            timestamp = c.long("timestamp"),
                            tags = c.string("tags").lines().filter { it.isNotBlank() },
                            energyBefore = c.int("energy_before"),
                            energyAfter = c.int("energy_after"),
                            note = c.string("note")
                        )
                    )
                }
            }
        return result
    }

    private fun queryEncounters(): List<Encounter> {
        val result = mutableListOf<Encounter>()
        helper.readableDatabase.query(T_ENCOUNTER, null, null, null, null, null, "timestamp DESC")
            .use { c ->
                while (c.moveToNext()) {
                    result.add(
                        Encounter(
                            id = c.long("id"),
                            timestamp = c.long("timestamp"),
                            happened = c.string("happened"),
                            feeling = c.string("feeling"),
                            need = c.string("need"),
                            doNext = c.string("todo")
                        )
                    )
                }
            }
        return result
    }

    private fun queryMeals(): List<Meal> {
        val result = mutableListOf<Meal>()
        helper.readableDatabase.query(T_MEAL, null, null, null, null, null, "timestamp DESC")
            .use { c ->
                while (c.moveToNext()) {
                    result.add(
                        Meal(
                            id = c.long("id"),
                            timestamp = c.long("timestamp"),
                            kind = c.string("kind"),
                            content = c.string("content"),
                            medicine = c.int("medicine") == 1
                        )
                    )
                }
            }
        return result
    }

    private fun queryWeights(): List<Weight> {
        val result = mutableListOf<Weight>()
        helper.readableDatabase.query(T_WEIGHT, null, null, null, null, null, "timestamp DESC")
            .use { c ->
                while (c.moveToNext()) {
                    result.add(
                        Weight(
                            id = c.long("id"),
                            timestamp = c.long("timestamp"),
                            kg = c.getDouble(c.getColumnIndexOrThrow("kg"))
                        )
                    )
                }
            }
        return result
    }

    private fun queryMoves(): List<Move> {
        val result = mutableListOf<Move>()
        helper.readableDatabase.query(T_MOVE, null, null, null, null, null, "timestamp DESC")
            .use { c ->
                while (c.moveToNext()) {
                    result.add(
                        Move(
                            id = c.long("id"),
                            timestamp = c.long("timestamp"),
                            tag = c.string("tag"),
                            minutes = c.int("minutes"),
                            note = c.string("note")
                        )
                    )
                }
            }
        return result
    }

    private fun queryMoveTags(): List<CustomTag> {
        val result = mutableListOf<CustomTag>()
        helper.readableDatabase
            .query(T_MOVE_TAG, null, null, null, null, null, "used_count DESC, name ASC")
            .use { c ->
                while (c.moveToNext()) result.add(CustomTag(c.string("name"), c.int("used_count")))
            }
        return result
    }

    private fun queryDims(): List<LifeDims> {
        val result = mutableListOf<LifeDims>()
        helper.readableDatabase.query(T_DIMS, null, null, null, null, null, "timestamp DESC")
            .use { c ->
                while (c.moveToNext()) {
                    result.add(
                        LifeDims(
                            id = c.long("id"),
                            timestamp = c.long("timestamp"),
                            agency = c.int("agency"),
                            achievement = c.int("achievement"),
                            vitality = c.int("vitality"),
                            connection = c.int("connection"),
                            value = c.int("value"),
                            joy = c.int("joy")
                        )
                    )
                }
            }
        return result
    }

    private fun queryStances(): List<Stance> {
        val result = mutableListOf<Stance>()
        helper.readableDatabase.query(T_STANCE, null, null, null, null, null, "date DESC, id DESC")
            .use { c ->
                while (c.moveToNext()) {
                    result.add(
                        Stance(
                            id = c.long("id"),
                            date = c.string("date"),
                            keys = c.string("keys").lines().filter { it.isNotBlank() },
                            custom = c.string("custom"),
                            note = c.string("note"),
                            spoken = c.intOrNull("spoken"),
                            ts = c.longOrNull("ts") ?: 0L
                        )
                    )
                }
            }
        return result
    }

    private fun queryTags(): List<CustomTag> {
        val result = mutableListOf<CustomTag>()
        helper.readableDatabase
            .query(T_TAGS, null, null, null, null, null, "used_count DESC, name ASC")
            .use { c ->
                while (c.moveToNext()) {
                    result.add(CustomTag(c.string("name"), c.int("used_count")))
                }
            }
        return result
    }

    private fun queryHiddenTags(): List<String> {
        val result = mutableListOf<String>()
        helper.readableDatabase
            .query(T_HIDDEN_TAGS, null, null, null, null, null, null)
            .use { c ->
                while (c.moveToNext()) result.add(c.string("name"))
            }
        return result
    }

    private fun queryEmotionTags(): List<String> {
        val result = mutableListOf<String>()
        helper.readableDatabase
            .query(T_EMOTION_TAGS, null, null, null, null, null, "sort ASC, name ASC")
            .use { c ->
                while (c.moveToNext()) result.add(c.string("name"))
            }
        return result
    }

    private fun queryContacts(): List<Contact> {
        val result = mutableListOf<Contact>()
        helper.readableDatabase.query(T_CONTACT, null, null, null, null, null, "slot ASC")
            .use { c ->
                while (c.moveToNext()) {
                    result.add(
                        Contact(c.int("slot"), c.string("name"), c.string("phone"))
                    )
                }
            }
        return result
    }

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
    private fun Cursor.intOrNull(column: String): Int? {
        val idx = getColumnIndexOrThrow(column)
        return if (isNull(idx)) null else getInt(idx)
    }
    private fun Cursor.longOrNull(column: String): Long? {
        val idx = getColumnIndexOrThrow(column)
        return if (isNull(idx)) null else getLong(idx)
    }
}

/** 与 util/TimeUtil 的 DAY_START_HOUR 同一个数：改那边的常量，这边也要跟着改。 */
private const val DAY_START_HOUR_FOR_SQL = 6

/** 「今天」是逻辑日：早上六点前仍然算前一天。 */
fun todayKey(): String = logicalToday().toString()
