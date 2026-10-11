package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.radar.RadarViewModel
import org.junit.Assert.assertEquals
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
    fun `viewModel updates radar angle and distance and handles invalid data safely`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RadarViewModel(app)

        viewModel.processRawSerialInput("90,24.")
        val stateAfterValid = viewModel.uiState.value
        assertEquals(90, stateAfterValid.currentAngle)
        assertEquals(24f, stateAfterValid.measuredDistanceCm ?: -1f, 0.01f)
        assertTrue(stateAfterValid.isTargetDetected)
        assertEquals(1L, stateAfterValid.validPacketsCount)

        // Adjust radar angle to 135 degrees
        viewModel.setRadarAngle(135)
        assertEquals(135, viewModel.uiState.value.currentAngle)

        // Feed invalid serial packet and verify no crash + counter increments
        viewModel.processRawSerialInput("invalid_packet")
        assertEquals(1L, viewModel.uiState.value.invalidPacketsCount)
    }
}
