package com.example.radar

/**
 * Represents the result of parsing a single serial packet from the Arduino Uno R3.
 * Expected Arduino serial format at 115200 baud:
 *   angle,distance.
 * followed by a newline (e.g., "90,24.\n").
 */
sealed class SerialParseResult {
    data class ValidPacket(
        val angle: Int,
        val distanceCm: Float,
        val rawLine: String
    ) : SerialParseResult()

    data class InvalidPacket(
        val rawLine: String,
        val reason: String
    ) : SerialParseResult()
}

object SerialPacketParser {

    const val MIN_ANGLE = 0
    const val MAX_ANGLE = 180
    const val MAX_SENSOR_DISTANCE_CM = 500f // HC-SR04 physical max ~400-450cm

    /**
     * Parses a single line or packet formatted as "angle,distance." or "angle,distance".
     * Safely handles no-echo readings (0, -1, or >500cm timeout values) and malformed strings
     * without throwing exceptions.
     */
    fun parsePacket(rawInput: String): SerialParseResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return SerialParseResult.InvalidPacket(rawInput, "Empty line")
        }

        // Strip trailing period(s) as specified by "angle,distance."
        val cleaned = trimmed.trimEnd('.')
        val parts = cleaned.split(',')
        if (parts.size != 2) {
            return SerialParseResult.InvalidPacket(
                rawLine = trimmed,
                reason = "Expected 'angle,distance.' format"
            )
        }

        val angleStr = parts[0].trim()
        val distanceStr = parts[1].trim()

        val angleFloat = angleStr.toFloatOrNull()
            ?: return SerialParseResult.InvalidPacket(
                rawLine = trimmed,
                reason = "Invalid angle '$angleStr'"
            )

        val distanceFloat = distanceStr.toFloatOrNull()
            ?: return SerialParseResult.InvalidPacket(
                rawLine = trimmed,
                reason = "Invalid distance '$distanceStr'"
            )

        if (!angleFloat.isFinite() || !distanceFloat.isFinite()) {
            return SerialParseResult.InvalidPacket(
                rawLine = trimmed,
                reason = "Non-finite value"
            )
        }

        val angleInt = angleFloat.toInt()
        if (angleInt < MIN_ANGLE || angleInt > MAX_ANGLE) {
            return SerialParseResult.InvalidPacket(
                rawLine = trimmed,
                reason = "Angle $angleInt° outside 0°..180° range"
            )
        }

        // Negative values other than -1 (which some sketches use for no-echo) or extreme negatives are invalid
        if (distanceFloat < -1f) {
            return SerialParseResult.InvalidPacket(
                rawLine = trimmed,
                reason = "Negative distance $distanceFloat cm"
            )
        }

        // Normalize no-echo readings (-1, 0, or >500cm sensor timeout) to 0f (no object detected)
        val normalizedDistanceCm = if (distanceFloat <= 0f || distanceFloat > MAX_SENSOR_DISTANCE_CM) {
            0f
        } else {
            distanceFloat
        }

        return SerialParseResult.ValidPacket(
            angle = angleInt,
            distanceCm = normalizedDistanceCm,
            rawLine = if (trimmed.endsWith(".")) trimmed else "$trimmed."
        )
    }
}

/**
 * Stateful stream buffer that accumulates raw chunks read from USB bulk transfer
 * and extracts complete lines/packets safely without crashing on buffer overflow or garbage data.
 */
class SerialStreamBuffer(
    private val maxBufferLength: Int = 4096
) {
    private val buffer = StringBuilder()

    /**
     * Appends a chunk of text decoded from USB serial bytes and returns all complete
     * parsed results found so far.
     *
     * Supports both standard newline-delimited packets ("90,24.\n", "90,24.\r\n")
     * and period-delimited packets ("90,24.91,25.") in case the Arduino sketch
     * omits newlines or sends back-to-back packets.
     */
    @Synchronized
    fun appendChunk(chunk: String): List<SerialParseResult> {
        if (chunk.isEmpty()) return emptyList()

        // Guard against runaway memory if a device streams un-delimited noise
        if (buffer.length + chunk.length > maxBufferLength) {
            buffer.clear()
        }

        buffer.append(chunk)
        val results = mutableListOf<SerialParseResult>()

        while (true) {
            val newlineIdx = buffer.indexOfFirst { it == '\n' || it == '\r' }
            if (newlineIdx >= 0) {
                val line = buffer.substring(0, newlineIdx).trim()
                // Consume consecutive \r or \n characters
                var consumeEnd = newlineIdx + 1
                while (consumeEnd < buffer.length && (buffer[consumeEnd] == '\n' || buffer[consumeEnd] == '\r')) {
                    consumeEnd++
                }
                buffer.delete(0, consumeEnd)

                if (line.isNotEmpty()) {
                    results.addAll(parseLineWithPossibleMultiPackets(line))
                }
                continue
            }

            // Also check if there is a complete "angle,distance." packet terminated by '.'
            // followed by whitespace or another digit, when no newline has arrived yet.
            val dotIdx = findTerminatingDotIndex(buffer)
            if (dotIdx >= 0) {
                val packetStr = buffer.substring(0, dotIdx + 1).trim()
                buffer.delete(0, dotIdx + 1)
                if (packetStr.isNotEmpty()) {
                    results.add(SerialPacketParser.parsePacket(packetStr))
                }
                continue
            }

            break
        }

        return results
    }

    private fun parseLineWithPossibleMultiPackets(line: String): List<SerialParseResult> {
        val trimmed = line.trim()
        val commaCount = trimmed.count { it == ',' }
        if (commaCount <= 1) {
            return listOf(SerialPacketParser.parsePacket(trimmed))
        }

        val results = mutableListOf<SerialParseResult>()
        val rawTokens = trimmed.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        for (token in rawTokens) {
            results.add(SerialPacketParser.parsePacket("$token."))
        }
        return results
    }

    private fun findTerminatingDotIndex(sb: CharSequence): Int {
        val commaIdx = sb.indexOf(',')
        if (commaIdx < 0) return -1
        for (i in (commaIdx + 1) until sb.length) {
            if (sb[i] == '.') {
                val substrAfterComma = sb.subSequence(commaIdx + 1, i)
                if (substrAfterComma.contains('.')) {
                    return i
                }
            }
        }
        return -1
    }

    @Synchronized
    fun clear() {
        buffer.clear()
    }
}
