package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.EqPresets
import com.example.audio.EqUiState
import com.example.data.model.EqPreset
import com.example.ui.components.GPlusFlatCard
import com.example.ui.theme.GPlusAmber
import com.example.ui.theme.GPlusBlue
import com.example.ui.theme.GPlusGreen
import com.example.ui.theme.GPlusRed
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    eqState: EqUiState,
    serviceConnected: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (EqPreset) -> Unit,
    onResetFlat: () -> Unit,
    onUpdateToneSliders: (bassDb: Float, midsDb: Float, trebleDb: Float) -> Unit,
    onUpdateBand: (Int, Float) -> Unit,
    onUpdateBassBoost: (Int) -> Unit,
    onUpdateVirtualizer: (Int) -> Unit,
    onUpdatePreamp: (Float) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Master Switch & Audio Playback Service Status
        item {
            GPlusFlatCard(accentStripColor = GPlusRed) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("KOREKTOR GRAFICZNY SONG/Q PRO", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Preset: ${eqState.activePresetName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = eqState.enabled,
                            onCheckedChange = onToggleEnabled,
                            colors = SwitchDefaults.colors(checkedTrackColor = GPlusRed),
                            modifier = Modifier.testTag("eq_master_switch")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val svcLabel = if (serviceConnected) "Usługa odtwarzania: Połączona" else "Usługa: Inicjalizacja"
                        val dspLabel = if (eqState.hardwareAttached) {
                            "DSP Sesja #${eqState.audioSessionId}"
                        } else {
                            "DSP Gotowy"
                        }
                        Text(svcLabel, style = MaterialTheme.typography.labelSmall, color = GPlusGreen)
                        Text(dspLabel, style = MaterialTheme.typography.labelSmall, color = GPlusBlue)
                    }
                }
            }
        }

        // Preset selector strip + Reset Flat
        item {
            GPlusFlatCard(accentStripColor = GPlusAmber) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PRESETY STUDYJNE", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(
                            onClick = onResetFlat,
                            shape = RectangleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("eq_reset_flat_button")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Flat 0 dB", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EqPresets.all.forEach { preset ->
                            val selected = preset.name == eqState.activePresetName
                            Box(
                                modifier = Modifier
                                    .background(if (selected) GPlusRed else MaterialTheme.colorScheme.surfaceVariant, RectangleShape)
                                    .border(1.dp, if (selected) GPlusRed else MaterialTheme.colorScheme.outline)
                                    .clickable { onSelectPreset(preset) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("eq_preset_${preset.name}")
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bass / Mids / Treble Preset Tone Sliders
        item {
            GPlusFlatCard(accentStripColor = GPlusRed) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SUWAKI TONÓW: BASS / MIDS / TREBLE", style = MaterialTheme.typography.labelMedium)

                    ToneSliderRow(
                        label = "Bass (Niskie • 60–230 Hz)",
                        valueDb = eqState.bassDb,
                        accentColor = GPlusRed,
                        enabled = eqState.enabled,
                        testTag = "eq_slider_bass",
                        onValueChange = { newBass ->
                            onUpdateToneSliders(newBass, eqState.midsDb, eqState.trebleDb)
                        }
                    )

                    ToneSliderRow(
                        label = "Mids (Średnie • 910 Hz)",
                        valueDb = eqState.midsDb,
                        accentColor = GPlusBlue,
                        enabled = eqState.enabled,
                        testTag = "eq_slider_mids",
                        onValueChange = { newMids ->
                            onUpdateToneSliders(eqState.bassDb, newMids, eqState.trebleDb)
                        }
                    )

                    ToneSliderRow(
                        label = "Treble (Wysokie • 3.6–14 kHz)",
                        valueDb = eqState.trebleDb,
                        accentColor = GPlusGreen,
                        enabled = eqState.enabled,
                        testTag = "eq_slider_treble",
                        onValueChange = { newTreble ->
                            onUpdateToneSliders(eqState.bassDb, eqState.midsDb, newTreble)
                        }
                    )
                }
            }
        }

        // Real-time EQ Frequency Response Curve Canvas
        item {
            GPlusFlatCard(accentStripColor = GPlusBlue) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("CHARAKTERYSTYKA PASMA (-15 dB / +15 dB)", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Color(0xFF1A1A1A))
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        val midY = size.height / 2f
                        drawLine(
                            color = Color.White.copy(alpha = 0.16f),
                            start = Offset(0f, midY),
                            end = Offset(size.width, midY),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, size.height * 0.25f),
                            end = Offset(size.width, size.height * 0.25f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, size.height * 0.75f),
                            end = Offset(size.width, size.height * 0.75f),
                            strokeWidth = 1f
                        )

                        val bands = eqState.bands
                        if (bands.isNotEmpty()) {
                            val stepX = size.width / (bands.size - 1).coerceAtLeast(1)
                            val path = Path()
                            bands.forEachIndexed { idx, band ->
                                val x = idx * stepX
                                val norm = (band.levelDb / 15f).coerceIn(-1f, 1f)
                                val y = midY - norm * (size.height * 0.42f)
                                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                drawCircle(
                                    color = if (eqState.enabled) GPlusAmber else Color.Gray,
                                    radius = 7f,
                                    center = Offset(x, y)
                                )
                            }
                            drawPath(
                                path = path,
                                color = if (eqState.enabled) GPlusRed else Color.Gray,
                                style = Stroke(width = 5f)
                            )
                        }
                    }
                }
            }
        }

        // 5-Band Precision Faders
        item {
            GPlusFlatCard(accentStripColor = GPlusGreen) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PRECYZYJNE PASMA CZĘSTOTLIWOŚCI (5-BAND DSP)", style = MaterialTheme.typography.labelMedium)
                    eqState.bands.forEach { band ->
                        ToneSliderRow(
                            label = band.label,
                            valueDb = band.levelDb,
                            accentColor = GPlusRed,
                            enabled = eqState.enabled,
                            testTag = "eq_band_${band.index}",
                            onValueChange = { onUpdateBand(band.index, it) }
                        )
                    }
                }
            }
        }

        // Bass Boost, Virtualizer 3D & Preamp
        item {
            GPlusFlatCard(accentStripColor = GPlusBlue) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("EFEKTY PRZESTRZENNE & WZMOCNIENIE", style = MaterialTheme.typography.labelMedium)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bass Boost", style = MaterialTheme.typography.titleMedium)
                        Text("${eqState.bassBoostStrength / 10}%", style = MaterialTheme.typography.labelMedium, color = GPlusBlue)
                    }
                    Slider(
                        value = eqState.bassBoostStrength.toFloat(),
                        onValueChange = { onUpdateBassBoost(it.roundToInt()) },
                        valueRange = 0f..1000f,
                        enabled = eqState.enabled,
                        colors = SliderDefaults.colors(thumbColor = GPlusBlue, activeTrackColor = GPlusBlue)
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Virtualizer 3D Stage", style = MaterialTheme.typography.titleMedium)
                        Text("${eqState.virtualizerStrength / 10}%", style = MaterialTheme.typography.labelMedium, color = GPlusGreen)
                    }
                    Slider(
                        value = eqState.virtualizerStrength.toFloat(),
                        onValueChange = { onUpdateVirtualizer(it.roundToInt()) },
                        valueRange = 0f..1000f,
                        enabled = eqState.enabled,
                        colors = SliderDefaults.colors(thumbColor = GPlusGreen, activeTrackColor = GPlusGreen)
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Przedwzmacniacz (Preamp .MFA)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${if (eqState.preampDb >= 0) "+" else ""}${(eqState.preampDb * 10).roundToInt() / 10f} dB",
                            style = MaterialTheme.typography.labelMedium,
                            color = GPlusAmber
                        )
                    }
                    Slider(
                        value = eqState.preampDb,
                        onValueChange = onUpdatePreamp,
                        valueRange = -6f..6f,
                        enabled = eqState.enabled,
                        colors = SliderDefaults.colors(thumbColor = GPlusAmber, activeTrackColor = GPlusAmber)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToneSliderRow(
    label: String,
    valueDb: Float,
    accentColor: Color,
    enabled: Boolean,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${if (valueDb >= 0) "+" else ""}${(valueDb * 10).roundToInt() / 10f} dB",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
        Slider(
            value = valueDb,
            onValueChange = onValueChange,
            valueRange = -15f..15f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
