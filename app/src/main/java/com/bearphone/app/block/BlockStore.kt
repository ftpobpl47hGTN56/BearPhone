package com.bearphone.app.block

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Правило блокировки.
 *  - точный номер: "+79123456789", "8 912 345-67-89" (одно и то же)
 *  - маска: '*' — любые цифры, '?' — одна цифра. "8800*", "+7495*", "+7912???????"
 */
data class BlockRule(val pattern: String, val note: String = "")

data class BlockedCall(val number: String?, val timeMillis: Long, val reason: String)

data class BlockSettings(
    val rules: List<BlockRule> = emptyList(),
    val blockHidden: Boolean = false,
    val blockNotInContacts: Boolean = false,
    /** true — заблокированные не попадают в системный журнал звонков */
    val skipCallLog: Boolean = false,
    /** true — тихое уведомление "Заблокирован звонок от …" */
    val notify: Boolean = true,
    val log: List<BlockedCall> = emptyList(),
)

/** Хранилище правил (JSON в SharedPreferences — без базы данных, мгновенно). */
object BlockStore {

    private const val LOG_LIMIT = 100
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(BlockSettings())
    val state: StateFlow<BlockSettings> = _state.asStateFlow()

    /** Можно звать сколько угодно раз — читает с диска только первый. */
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("bear_block", Context.MODE_PRIVATE)
        prefs = p
        _state.value = BlockSettings(
            rules = parseRules(p.getString(K_RULES, null)),
            blockHidden = p.getBoolean(K_HIDDEN, false),
            blockNotInContacts = p.getBoolean(K_NOT_CONTACTS, false),
            skipCallLog = p.getBoolean(K_SKIP_LOG, false),
            notify = p.getBoolean(K_NOTIFY, true),
            log = parseLog(p.getString(K_LOG, null)),
        )
    }

    fun update(transform: (BlockSettings) -> BlockSettings) {
        val s = transform(_state.value)
        _state.value = s
        prefs?.edit()
            ?.putString(K_RULES, JSONArray(s.rules.map { JSONObject().put("p", it.pattern).put("n", it.note) }).toString())
            ?.putBoolean(K_HIDDEN, s.blockHidden)
            ?.putBoolean(K_NOT_CONTACTS, s.blockNotInContacts)
            ?.putBoolean(K_SKIP_LOG, s.skipCallLog)
            ?.putBoolean(K_NOTIFY, s.notify)
            ?.putString(K_LOG, JSONArray(s.log.map {
                JSONObject().put("num", it.number ?: "").put("t", it.timeMillis).put("r", it.reason)
            }).toString())
            ?.apply()
    }

    // ---------- правила ----------

    /** @return false, если такое правило уже есть или строка пустая */
    fun addRule(pattern: String, note: String = ""): Boolean {
        val clean = pattern.trim()
        if (NumberMatcher.key(clean).isEmpty()) return false
        if (_state.value.rules.any { NumberMatcher.key(it.pattern) == NumberMatcher.key(clean) }) return false
        update { it.copy(rules = listOf(BlockRule(clean, note.trim())) + it.rules) }
        return true
    }

    fun removeRule(rule: BlockRule) = update { it.copy(rules = it.rules - rule) }

    /** Правило, которое блокирует этот номер (для кнопки "Разблокировать" в журнале). */
    fun ruleFor(number: String?): BlockRule? =
        _state.value.rules.firstOrNull { NumberMatcher.matches(it.pattern, number) }

    fun isExactlyBlocked(number: String?): BlockRule? =
        _state.value.rules.firstOrNull {
            !NumberMatcher.isMask(it.pattern) && NumberMatcher.matches(it.pattern, number)
        }

    // ---------- журнал заблокированных ----------

    fun recordBlocked(number: String?, reason: String) = update {
        it.copy(log = (listOf(BlockedCall(number, System.currentTimeMillis(), reason)) + it.log).take(LOG_LIMIT))
    }

    fun clearLog() = update { it.copy(log = emptyList()) }

    // ---------- внутреннее ----------

    private fun parseRules(json: String?): List<BlockRule> = try {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { BlockRule(it.getString("p"), it.optString("n")) } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseLog(json: String?): List<BlockedCall> = try {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let {
                BlockedCall(it.optString("num").ifBlank { null }, it.getLong("t"), it.optString("r"))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private const val K_RULES = "rules"
    private const val K_HIDDEN = "block_hidden"
    private const val K_NOT_CONTACTS = "block_not_in_contacts"
    private const val K_SKIP_LOG = "skip_call_log"
    private const val K_NOTIFY = "notify"
    private const val K_LOG = "blocked_log"
}

/**
 * Сравнение номеров с учётом российского формата:
 * "+7 912 345-67-89", "8 (912) 345-67-89" и "9123456789" — один и тот же номер.
 */
object NumberMatcher {

    fun isMask(pattern: String) = pattern.any { it == '*' || it == '?' }

    /**
     * Нормальная форма: только цифры (и * ? для масок), российские 8XXXXXXXXXX / XXXXXXXXXX -> 7XXXXXXXXXX.
     * Номер, начинавшийся с '+', не трогаем (+86..., +380... — иностранные).
     */
    fun key(raw: String): String {
        val t = raw.trim()
        val plus = t.startsWith("+")
        var d = t.filter { it.isDigit() || it == '*' || it == '?' }
        if (!plus) {
            val digitsOnly = d.all { it.isDigit() }
            when {
                d.startsWith("8") && (d.length == 11 || !digitsOnly) -> d = "7" + d.drop(1)
                digitsOnly && d.length == 10 && d.startsWith("9") -> d = "7$d"
            }
        }
        return d
    }

    fun matches(pattern: String, number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        val n = key(number)
        if (n.isEmpty()) return false
        val p = key(pattern)
        if (!isMask(p)) return p == n
        val regex = buildString {
            append('^')
            p.forEach { ch ->
                when (ch) {
                    '*' -> append("\\d*")
                    '?' -> append("\\d")
                    else -> append(ch)
                }
            }
            append('$')
        }
        return Regex(regex).matches(n)
    }
}
