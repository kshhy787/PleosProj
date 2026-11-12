package app.gildongmu.core

/** Thrown when JSON text is malformed or a required field is missing or of the wrong type. */
class JsonException(message: String) : Exception(message)

/**
 * A small JSON reader with no dependencies, so the host engine and its tests run on a plain JVM
 * as well as on Android. It reads the route package and the demo scenario; it is not a general
 * purpose library (no streaming, no writing).
 *
 * Values come back as: [JsonObject], [List], [String], [Double], [Boolean] or null.
 */
object Json {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) reader.fail("Unexpected text after the JSON value")
        return value
    }

    fun parseObject(text: String): JsonObject =
        parse(text) as? JsonObject ?: throw JsonException("Expected a JSON object at the top level")

    private class Reader(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun fail(message: String): Nothing {
            var line = 1
            var column = 1
            for (k in 0 until minOf(i, s.length)) {
                if (s[k] == '\n') {
                    line++
                    column = 1
                } else {
                    column++
                }
            }
            throw JsonException("$message (line $line, column $column)")
        }

        fun skipWhitespace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun readValue(): Any? {
            if (atEnd()) fail("Unexpected end of JSON")
            return when (val c = s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else fail("Unexpected character '$c'")
            }
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) fail("Expected '$word'")
            i += word.length
            return value
        }

        private fun readNumber(): Double {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(start, i).toDoubleOrNull() ?: fail("Malformed number")
        }

        private fun readString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) fail("Unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("Truncated \\u escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("Malformed \\u escape")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> fail("Unknown escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readArray(): List<Any?> {
            i++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (!atEnd() && s[i] == ']') {
                i++
                return list
            }
            while (true) {
                skipWhitespace()
                list.add(readValue())
                skipWhitespace()
                if (atEnd()) fail("Unterminated array")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> fail("Expected ',' or ']' in array")
                }
            }
        }

        private fun readObject(): JsonObject {
            i++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd() && s[i] == '}') {
                i++
                return JsonObject(map, "")
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || s[i] != '"') fail("Expected a string key in object")
                val key = readString()
                skipWhitespace()
                if (atEnd() || s[i] != ':') fail("Expected ':' after key \"$key\"")
                i++
                skipWhitespace()
                map[key] = readValue()
                skipWhitespace()
                if (atEnd()) fail("Unterminated object")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return JsonObject(map, "")
                    else -> fail("Expected ',' or '}' in object")
                }
            }
        }
    }
}

/**
 * A JSON object with typed accessors. Errors name the full path of the offending field
 * ("pois[2].story.variants[0].text"), which matters when a generated package is rejected.
 */
class JsonObject internal constructor(private val map: Map<String, Any?>, private val path: String) {

    val keys: Set<String> get() = map.keys

    fun has(key: String): Boolean = map[key] != null

    private fun at(key: String) = if (path.isEmpty()) key else "$path.$key"

    private fun missing(key: String): Nothing = throw JsonException("Missing field \"${at(key)}\"")

    private fun wrong(key: String, expected: String): Nothing =
        throw JsonException("Field \"${at(key)}\" should be $expected")

    fun string(key: String): String = optString(key) ?: missing(key)

    fun optString(key: String): String? = when (val v = map[key]) {
        null -> null
        is String -> v
        else -> wrong(key, "a string")
    }

    fun double(key: String): Double = optDouble(key) ?: missing(key)

    fun optDouble(key: String): Double? = when (val v = map[key]) {
        null -> null
        is Double -> v
        else -> wrong(key, "a number")
    }

    fun int(key: String): Int = optInt(key) ?: missing(key)

    fun optInt(key: String): Int? = optDouble(key)?.let { d ->
        if (d != Math.floor(d) || d < Int.MIN_VALUE || d > Int.MAX_VALUE) wrong(key, "a whole number")
        d.toInt()
    }

    fun optBoolean(key: String): Boolean? = when (val v = map[key]) {
        null -> null
        is Boolean -> v
        else -> wrong(key, "true or false")
    }

    fun obj(key: String): JsonObject = optObj(key) ?: missing(key)

    fun optObj(key: String): JsonObject? = when (val v = map[key]) {
        null -> null
        is JsonObject -> JsonObject(v.map, at(key))
        else -> wrong(key, "an object")
    }

    /** The raw elements of an array field; an absent field reads as an empty list. */
    fun array(key: String): List<Any?> = when (val v = map[key]) {
        null -> emptyList()
        is List<*> -> v
        else -> wrong(key, "an array")
    }

    fun objects(key: String): List<JsonObject> = array(key).mapIndexed { index, v ->
        (v as? JsonObject)?.let { JsonObject(it.map, "${at(key)}[$index]") }
            ?: throw JsonException("Field \"${at(key)}[$index]\" should be an object")
    }

    fun strings(key: String): List<String> = array(key).mapIndexed { index, v ->
        v as? String ?: throw JsonException("Field \"${at(key)}[$index]\" should be a string")
    }
}
