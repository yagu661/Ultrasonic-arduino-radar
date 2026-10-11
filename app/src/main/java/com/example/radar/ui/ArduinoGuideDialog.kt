package com.example.radar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.RadarBlack
import com.example.ui.theme.RadarBorderGreen
import com.example.ui.theme.RadarCyanInfo
import com.example.ui.theme.RadarGreenBright
import com.example.ui.theme.RadarSurfaceDark
import com.example.ui.theme.RadarSurfaceElevated
import com.example.ui.theme.TextPhosphorMuted
import com.example.ui.theme.TextPhosphorWhite

private const val ARDUINO_UNO_SKETCH = """// Arduino Uno R3 + HC-SR04 Ultrasonic Radar (Manual Hand-Scan, No Servo Required)
// Baud Rate: 115200 | Output Format: angle,distance.\n  (Example: 90,24.)

const int TRIG_PIN = 9;
const int ECHO_PIN = 10;
int reportedAngle = 90; // Synchronized with manual angle control in Android app

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
}

int measureDistanceCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  long duration = pulseIn(ECHO_PIN, HIGH, 30000); // 30ms timeout (~500cm)
  if (duration <= 0) return 0; // 0 = no echo / clear sector
  return (int)(duration * 0.0343 / 2.0);
}

void loop() {
  // Optional: Keep reportedAngle in sync when adjusted in Android app
  if (Serial.available() > 0) {
    int incomingAngle = Serial.parseInt();
    while (Serial.available() > 0) Serial.read();
    if (incomingAngle >= 0 && incomingAngle <= 180) {
      reportedAngle = incomingAngle;
    }
  }

  int distanceCm = measureDistanceCm();

  // Exact format: angle,distance. followed by newline
  Serial.print(reportedAngle);
  Serial.print(",");
  Serial.print(distanceCm);
  Serial.println(".");

  delay(80);
}"""

@Composable
fun ArduinoGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, RadarBorderGreen, RoundedCornerShape(20.dp)),
            color = RadarSurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Hardware Setup",
                            tint = RadarGreenBright,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Hand-Scan Setup & Arduino Sketch",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPhosphorWhite
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_guide_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Guide",
                            tint = TextPhosphorMuted
                        )
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.img_arduino_wiring_guide_1791710270395),
                    contentDescription = "Arduino Uno R3 USB OTG and HC-SR04 Wiring Schematic",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, RadarBorderGreen, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RadarSurfaceElevated)
                        .border(1.dp, RadarBorderGreen, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = RadarCyanInfo,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MANUAL HAND-SCAN WORKFLOW",
                            style = MaterialTheme.typography.labelLarge,
                            color = RadarCyanInfo
                        )
                    }
                    Text(
                        text = "1. Point your HC-SR04 sensor at an angle (e.g., 90°) and set the same angle in the app's Manual Angle Control.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPhosphorWhite
                    )
                    Text(
                        text = "2. Objects detected within range draw a RED 15° FILLED WEDGE with the distance in cm next to the area.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPhosphorWhite
                    )
                    Text(
                        text = "3. Rotate the sensor by hand to new angles to build a full map. Previous detections stay on screen until you rescan that angle and confirm the object is gone (turning that sector GREEN).",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPhosphorWhite
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RadarSurfaceElevated)
                        .border(1.dp, RadarBorderGreen, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = RadarGreenBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HC-SR04 + ARDUINO UNO R3 WIRING (NO SERVO NEEDED)",
                            style = MaterialTheme.typography.labelLarge,
                            color = RadarGreenBright
                        )
                    }
                    Text(
                        text = "• HC-SR04 VCC → Arduino 5V   |   HC-SR04 GND → Arduino GND\n" +
                            "• HC-SR04 TRIG → Digital Pin 9   |   HC-SR04 ECHO → Digital Pin 10\n" +
                            "• Serial Config: 115200 baud, 8N1, format: angle,distance.\\n (e.g. 90,24.)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPhosphorWhite
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ARDUINO UNO R3 SKETCH (115200 BAUD)",
                        style = MaterialTheme.typography.labelLarge,
                        color = RadarGreenBright
                    )
                    Button(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("Arduino Ultrasonic Radar Sketch", ARDUINO_UNO_SKETCH)
                            )
                            Toast.makeText(
                                context,
                                "Arduino Uno R3 sketch copied to clipboard!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RadarGreenBright,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.testTag("copy_arduino_sketch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Sketch",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Sketch", fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = ARDUINO_UNO_SKETCH,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RadarGreenBright,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RadarBlack)
                        .border(1.dp, RadarBorderGreen, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                )
            }
        }
    }
}
