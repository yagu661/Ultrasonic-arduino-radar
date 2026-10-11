package com.example

import com.example.radar.SerialPacketParser
import com.example.radar.SerialParseResult
import com.example.radar.SerialStreamBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parseValidArduinoPacket_withTrailingDotAndNewline() {
        val result = SerialPacketParser.parsePacket("90,24.\n")
        assertTrue(result is SerialParseResult.ValidPacket)
        val valid = result as SerialParseResult.ValidPacket
        assertEquals(90, valid.angle)
        assertEquals(24f, valid.distanceCm, 0.01f)
    }

    @Test
    fun parseBoundaryAngles_0and180() {
        val r0 = SerialPacketParser.parsePacket("0,15.") as SerialParseResult.ValidPacket
        assertEquals(0, r0.angle)
        assertEquals(15f, r0.distanceCm, 0.01f)

        val r180 = SerialPacketParser.parsePacket("180,142.") as SerialParseResult.ValidPacket
        assertEquals(180, r180.angle)
        assertEquals(142f, r180.distanceCm, 0.01f)
    }

    @Test
    fun parseNoEchoReadings_normalizesToZeroDistance() {
        val zeroEcho = SerialPacketParser.parsePacket("90,0.") as SerialParseResult.ValidPacket
        assertEquals(0f, zeroEcho.distanceCm, 0.01f)

        val minusOneEcho = SerialPacketParser.parsePacket("90,-1.") as SerialParseResult.ValidPacket
        assertEquals(0f, minusOneEcho.distanceCm, 0.01f)

        val timeoutEcho = SerialPacketParser.parsePacket("90,999.") as SerialParseResult.ValidPacket
        assertEquals(0f, timeoutEcho.distanceCm, 0.01f)
    }

    @Test
    fun parseInvalidSerialData_doesNotCrash() {
        val outOfBoundsAngle = SerialPacketParser.parsePacket("245,30.")
        assertTrue(outOfBoundsAngle is SerialParseResult.InvalidPacket)

        val negativeDistance = SerialPacketParser.parsePacket("90,-5.")
        assertTrue(negativeDistance is SerialParseResult.InvalidPacket)

        val malformedNoise = SerialPacketParser.parsePacket("corrupted_usb_bytes")
        assertTrue(malformedNoise is SerialParseResult.InvalidPacket)
    }

    @Test
    fun streamBuffer_reassemblesSplitUsbChunks() {
        val streamBuffer = SerialStreamBuffer()
        val firstRead = streamBuffer.appendChunk("90,")
        assertTrue(firstRead.isEmpty())

        val secondRead = streamBuffer.appendChunk("24.\n45,18.\r\n")
        assertEquals(2, secondRead.size)
        val p1 = secondRead[0] as SerialParseResult.ValidPacket
        val p2 = secondRead[1] as SerialParseResult.ValidPacket
        assertEquals(90, p1.angle)
        assertEquals(24f, p1.distanceCm, 0.01f)
        assertEquals(45, p2.angle)
        assertEquals(18f, p2.distanceCm, 0.01f)
    }
}
