package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.radar.RadarViewModel
import com.example.radar.SectorScanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ultrasonic Radar", appName)
    }

    @Test
    fun `viewModel builds persistent sector map and turns rescanned empty sector green`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RadarViewModel(app)

        // 1. Scan at manual angle 90° with object at 24 cm
        viewModel.setRadarAngle(90)
        viewModel.processRawSerialInput("0,24.") // Manual angle mode keeps angle synchronized at 90°
        val stateAt90 = viewModel.uiState.value
        assertEquals(90, stateAt90.currentAngle)
        assertEquals(24f, stateAt90.measuredDistanceCm ?: -1f, 0.01f)
        assertEquals(SectorScanStatus.DETECTED_RED, stateAt90.echoPoints[90]?.status)

        // 2. Rotate sensor by hand to 45° and scan object at 40 cm
        viewModel.setRadarAngle(45)
        viewModel.processRawSerialInput("0,40.")
        val stateAt45 = viewModel.uiState.value
        assertEquals(45, stateAt45.currentAngle)
        // Both 90° and 45° detections remain in the map!
        assertEquals(SectorScanStatus.DETECTED_RED, stateAt45.echoPoints[90]?.status)
        assertEquals(SectorScanStatus.DETECTED_RED, stateAt45.echoPoints[45]?.status)
        assertEquals(2, stateAt45.activeDetectedCount)

        // 3. Rescan 90° after object is removed (0 cm = no echo) -> 90° turns CLEARED_GREEN while 45° stays DETECTED_RED
        viewModel.setRadarAngle(90)
        viewModel.processRawSerialInput("90,0.")
        val stateAfterRescan = viewModel.uiState.value
        assertEquals(SectorScanStatus.CLEARED_GREEN, stateAfterRescan.echoPoints[90]?.status)
        assertEquals(SectorScanStatus.DETECTED_RED, stateAfterRescan.echoPoints[45]?.status)
        assertEquals(1, stateAfterRescan.activeDetectedCount)
        assertEquals(1, stateAfterRescan.clearedSectorCount)

        // 4. Clear scan button resets map
        viewModel.clearRadarScope()
        assertTrue(viewModel.uiState.value.echoPoints.isEmpty())
        assertNotNull(viewModel.uiState.value)
    }
}
