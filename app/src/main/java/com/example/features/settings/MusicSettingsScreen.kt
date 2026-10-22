package com.example.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.SynapseNeonLogo
import com.example.components.formatDurationMillis
import com.example.data.AudioQualityPreset
import com.example.data.PlaybackState
import com.example.services.ContentProtectionReport
import com.example.ui.theme.HighlightFavoritePink
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.OledBlack
import com.example.ui.theme.SecondaryTextZinc
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SubtleBorderWhite
import com.example.ui.theme.SystemBlue
import com.example.ui.theme.SystemGreen

@Composable
fun MusicSettingsScreen(
    audioQuality: AudioQualityPreset,
    gaplessPlaybackEnabled: Boolean,
    playbackState: PlaybackState,
    protectionReport: ContentProtectionReport,
    notificationPermissionGranted: Boolean,
    statusMessage: String?,
    onSelectAudioQuality: (AudioQualityPreset) -> Unit,
    onToggleGaplessPlayback: (Boolean) -> Unit,
    onSetSleepTimerMinutes: (Int?) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRescanLibrary: () -> Unit,
    onClearCachedArtwork: () -> Unit,
    onToggleContentProtection: (Boolean) -> Unit,
    onAddCustomBlockedDomain: (String) -> Unit,
    onRemoveCustomBlockedDomain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var customDomainInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .statusBarsPadding()
                .testTag("music_settings_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 164.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    color = LabelPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Configure AndroidX Media3 playback, library maintenance, and network protection",
                    color = SecondaryTextZinc,
                    fontSize = 13.sp
                )
            }

            if (!statusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = SystemGreen.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SystemGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = statusMessage,
                            color = SystemGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // 1. Audio Quality Preferences & Gapless Playback
            item {
                SettingsCardContainer(
                    title = "Audio & Playback Engine",
                    icon = Icons.Default.GraphicEq,
                    iconTint = SystemBlue
                ) {
                    AudioQualityPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectAudioQuality(preset) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = audioQuality == preset,
                                onClick = { onSelectAudioQuality(preset) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SystemBlue,
                                    unselectedColor = SecondaryTextZinc
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.label,
                                    color = LabelPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = preset.description,
                                    color = SecondaryTextZinc,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gapless Playback & Pre-buffering",
                                color = LabelPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Seamless transitions between queued tracks in ExoPlayer",
                                color = SecondaryTextZinc,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = gaplessPlaybackEnabled,
                            onCheckedChange = onToggleGaplessPlayback,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SystemBlue
                            ),
                            modifier = Modifier.testTag("settings_gapless_switch")
                        )
                    }
                }
            }

            // 2. Sleep Timer
            item {
                SettingsCardContainer(
                    title = "Sleep Timer",
                    icon = Icons.Default.Bedtime,
                    iconTint = SecondaryViolet
                ) {
                    Text(
                        text = if (playbackState.sleepTimerRemainingMs != null) {
                            "Active timer: pauses playback in ${formatDurationMillis(playbackState.sleepTimerRemainingMs)}"
                        } else {
                            "Automatically fade and pause ExoPlayer after a set duration"
                        },
                        color = if (playbackState.sleepTimerRemainingMs != null) SystemGreen else SecondaryTextZinc,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(null to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m").forEach { (mins, label) ->
                            FilterChip(
                                selected = (mins == null && playbackState.sleepTimerRemainingMs == null),
                                onClick = { onSetSleepTimerMinutes(mins) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SecondaryViolet.copy(alpha = 0.24f),
                                    selectedLabelColor = LabelPrimary
                                )
                            )
                        }
                    }
                }
            }

            // 3. Notification & MediaSession Controls Explanation
            item {
                SettingsCardContainer(
                    title = "Media Notification & Lock-Screen Controls",
                    icon = Icons.Default.NotificationsActive,
                    iconTint = SystemGreen
                ) {
                    Text(
                        text = "Synapse Music runs an AndroidX Media3 MediaSessionService so playback continues when the screen is locked or while switching apps, exposing album art, seek bar, and Play/Pause/Previous/Next controls.",
                        color = SecondaryTextZinc,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (notificationPermissionGranted) {
                                "Status: Notification permission granted"
                            } else {
                                "Status: Notification permission can be enabled for Android 13+"
                            },
                            color = if (notificationPermissionGranted) SystemGreen else SecondaryTextZinc,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (!notificationPermissionGranted) {
                            Button(
                                onClick = onRequestNotificationPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = SystemBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Allow", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 4. Library Rescan & Artwork Cache Maintenance
            item {
                SettingsCardContainer(
                    title = "Library & Cache Maintenance",
                    icon = Icons.Default.CleaningServices,
                    iconTint = SystemBlue
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRescanLibrary,
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_rescan_library_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rescan Library", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onClearCachedArtwork,
                            colors = ButtonDefaults.buttonColors(containerColor = MainSurface),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, SubtleBorderWhite, RoundedCornerShape(14.dp))
                                .testTag("settings_clear_artwork_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = HighlightFavoritePink,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Art Cache", color = LabelPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. Honest Content Protection (ContentBlockingService)
            item {
                SettingsCardContainer(
                    title = "Content Protection & Telemetry Filter",
                    icon = Icons.Default.Shield,
                    iconTint = SystemGreen
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = protectionReport.engineModeLabel,
                                color = LabelPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${protectionReport.activeFilterCount} active rules • ${protectionReport.blockedRequestsCount} telemetry/ad requests filtered",
                                color = SystemGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Switch(
                            checked = protectionReport.isEnabled,
                            onCheckedChange = onToggleContentProtection,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SystemGreen
                            ),
                            modifier = Modifier.testTag("settings_content_protection_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = protectionReport.platformDisclosure,
                        color = SecondaryTextZinc,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customDomainInput,
                            onValueChange = { customDomainInput = it },
                            placeholder = { Text("Add domain rule (e.g. ads.example.com)", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LabelPrimary,
                                unfocusedTextColor = LabelPrimary,
                                focusedBorderColor = SystemBlue,
                                unfocusedBorderColor = SubtleBorderWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_custom_rule_input")
                        )

                        IconButton(
                            onClick = {
                                if (customDomainInput.isNotBlank()) {
                                    onAddCustomBlockedDomain(customDomainInput)
                                    customDomainInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SystemBlue)
                                .testTag("settings_add_rule_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Custom Filter Rule",
                                tint = Color.White
                            )
                        }
                    }

                    if (protectionReport.customBlockedDomains.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        protectionReport.customBlockedDomains.forEach { domain ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "||$domain^",
                                    color = LabelPrimary,
                                    fontSize = 12.sp
                                )
                                IconButton(
                                    onClick = { onRemoveCustomBlockedDomain(domain) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove rule",
                                        tint = HighlightFavoritePink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. About Synapse Music
            item {
                SettingsCardContainer(
                    title = "About Synapse Music",
                    icon = Icons.Default.Info,
                    iconTint = SecondaryViolet
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SynapseNeonLogo(size = 44.dp)
                        Column {
                            Text(
                                text = "Synapse Music v2.0",
                                color = LabelPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Package: com.synapse.in • Engine: AndroidX Media3 ExoPlayer 1.5.1 + MediaSessionService",
                                color = SecondaryTextZinc,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCardContainer(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MainSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    color = LabelPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope
