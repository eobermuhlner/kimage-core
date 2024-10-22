package ch.obermuhlner.kimage.core.image.io.json

class JsonTokenizer(private val json: String) {
    private var position = 0

    fun nextToken(): String? {
        skipWhitespace()
        if (position >= json.length) return null

        return when (val char = json[position]) {
            '{', '}', '[', ']', ':', ',' -> {
                position++
                char.toString()
            }
            '"' -> parseString()
            in '0'..'9', '-', '+' -> parseNumber() // Handle negative numbers
            else -> parseLiteral()
        }
    }

    private fun skipWhitespace() {
        while (position < json.length && json[position].isWhitespace()) {
            position++
        }
    }

    private fun parseString(): String {
        val start = position++
        while (position < json.length && json[position] != '"') {
            if (json[position] == '\\') position++ // skip escaped characters
            position++
        }
        position++ // Skip closing quote
        return json.substring(start, position)
    }

    private fun parseNumber(): String {
        val start = position
        while (position < json.length && (json[position].isDigit() || json[position] == '.' || json[position] == '-' || json[position] == '+' || json[position] == 'e' || json[position] == 'E')) {
            position++
        }
        return json.substring(start, position)
    }

    private fun parseLiteral(): String {
        val start = position
        while (position < json.length && json[position].isLetter()) {
            position++
        }
        return json.substring(start, position)
    }
}
