package com.example.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdateAlt
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.SynapseNeonLogo
import com.example.components.formatDurationMillis
import com.example.data.AudioQualityPreset
import com.example.data.BeatifyPushNotificationItem
import com.example.data.BeatifySyncStatus
import com.example.data.OtaBundleManifest
import com.example.data.PlaybackState
import com.example.services.ContentProtectionReport
import com.example.ui.theme.BeatifyAppleCrimson
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighlightFavoritePink
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.OledBlack
import com.example.ui.theme.SecondaryTextZinc
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SubtleBorderWhite
import com.example.ui.theme.SystemBlue
import com.example.ui.theme.SystemGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicSettingsScreen(
    audioQuality: AudioQualityPreset,
    gaplessPlaybackEnabled: Boolean,
    playbackState: PlaybackState,
    protectionReport: ContentProtectionReport,
    notificationPermissionGranted: Boolean,
    statusMessage: String?,
    currentUserUid: String? = null,
    currentUserEmail: String? = null,
    currentUserDisplayName: String? = null,
    syncStatus: BeatifySyncStatus = BeatifySyncStatus.SignedOut,
    youTubeAccountConnected: Boolean = false,
    youTubeConnectedChannelLabel: String? = null,
    otaManifest: OtaBundleManifest = OtaBundleManifest(),
    notificationCategories: Set<String> = setOf("account", "music", "podcasts", "playlists", "updates", "system"),
    marketingOptIn: Boolean = false,
    pushNotifications: List<BeatifyPushNotificationItem> = emptyList(),
    onOpenAuthSheet: () -> Unit = {},
    onTriggerCloudSync: () -> Unit = {},
    onConnectYouTubeAccount: () -> Unit = {},
    onDisconnectYouTubeAccount: () -> Unit = {},
    onCheckOtaUpdate: () -> Unit = {},
    onSwitchOtaChannel: (String) -> Unit = {},
    onRollbackOtaBundle: () -> Unit = {},
    onToggleNotificationCategory: (String) -> Unit = {},
    onToggleMarketingOptIn: (Boolean) -> Unit = {},
    onTriggerSampleNotification: (String) -> Unit = {},
    onNavigateDeepLink: (String) -> Unit = {},
    onSelectAudioQuality: (AudioQualityPreset) -> Unit,
    onToggleGaplessPlayback: (Boolean) -> Unit,
    onSetCrossfadeDurationSeconds: (Int) -> Unit = {},
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
                    text = "Settings & Cloud Sync",
                    color = LabelPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Google Sign-In, YouTube Music Library Sync, Cloud Firestore & Media3 Audio",
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

            // 1. Google Account & Cloud Firestore Sync
            item {
                SettingsCardContainer(
                    title = "Google Account & Cloud Firestore",
                    icon = if (currentUserUid != null) Icons.Default.CloudDone else Icons.Default.AccountCircle,
                    iconTint = if (currentUserUid != null) SystemGreen else BeatifyAppleCrimson
                ) {
                    val statusDesc = when (syncStatus) {
                        is BeatifySyncStatus.Synced -> "Synced with Cloud Firestore • Real-time listeners active"
                        is BeatifySyncStatus.Loading -> "Synchronizing playlists, favorites & preferences..."
                        is BeatifySyncStatus.Offline -> "Offline • ${syncStatus.pendingOutboxCount} changes queued"
                        is BeatifySyncStatus.Error -> "Sync notice: ${syncStatus.message}"
                        is BeatifySyncStatus.SignedOut -> "Signed out • Local playback active"
                    }

                    Text(
                        text = if (currentUserUid != null) {
                            "${currentUserDisplayName ?: "Beatify Listener"} (${currentUserEmail ?: currentUserUid.take(10)})"
                        } else {
                            "Sign in with Google to synchronize playlists, favorites, subscriptions, and playback settings across devices."
                        },
                        color = LabelPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusDesc,
                        color = if (currentUserUid != null) SystemGreen else SecondaryTextZinc,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenAuthSheet,
                            colors = ButtonDefaults.buttonColors(containerColor = BeatifyAppleCrimson),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_google_sign_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentUserUid != null) "Manage Account" else "Continue with Google",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (currentUserUid != null) {
                            Button(
                                onClick = onTriggerCloudSync,
                                colors = ButtonDefaults.buttonColors(containerColor = SystemBlue),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_sync_now_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sync Now",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 1b. YouTube Account Connection & Library Sync (Separate from basic Google identity login)
            item {
                SettingsCardContainer(
                    title = "YouTube Account & Library Sync",
                    icon = Icons.Default.CloudSync,
                    iconTint = if (youTubeAccountConnected) SystemGreen else SystemBlue
                ) {
                    Text(
                        text = if (youTubeAccountConnected) {
                            "Connected: ${youTubeConnectedChannelLabel ?: (currentUserEmail ?: "YouTube Music Account")}"
                        } else {
                            "Connect your YouTube / YouTube Music library to import playlists, liked songs, and subscribed artist feeds."
                        },
                        color = LabelPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (youTubeAccountConnected) {
                            "Idempotent metadata sync active • Imported playlists and tracks are linked to your account without duplicates."
                        } else {
                            "Uses YouTube read-only catalog & playlist sync. Disconnect anytime without deleting local files."
                        },
                        color = if (youTubeAccountConnected) SystemGreen else SecondaryTextZinc,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onConnectYouTubeAccount,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (youTubeAccountConnected) SystemBlue else BeatifyAppleCrimson
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_connect_youtube_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (youTubeAccountConnected) "Sync YouTube Library" else "Connect YouTube Account",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (youTubeAccountConnected) {
                            Button(
                                onClick = onDisconnectYouTubeAccount,
                                colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, SubtleBorderWhite, RoundedCornerShape(14.dp))
                                    .testTag("settings_disconnect_youtube_button")
                            ) {
                                Text(
                                    text = "Disconnect YouTube",
                                    color = HighlightFavoritePink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Secure Over-The-Air (OTA) Web Bundle Updater
            item {
                SettingsCardContainer(
                    title = "Secure OTA Web Bundle Updates",
                    icon = Icons.Default.SystemUpdateAlt,
                    iconTint = SystemBlue
                ) {
                    Text(
                        text = "Native APK: v${otaManifest.targetNativeVersion} • Active Web Bundle: ${otaManifest.bundleVersion}",
                        color = LabelPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = otaManifest.statusLabel,
                        color = SystemGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Checksum: ${otaManifest.sha256Checksum.take(36)}... • No dynamic native code (.dex/.so) loading.",
                        color = SecondaryTextZinc,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Channel:",
                            color = SecondaryTextZinc,
                            fontSize = 12.sp
                        )
                        listOf("stable" to "Stable", "beta" to "Beta").forEach { (ch, label) ->
                            FilterChip(
                                selected = otaManifest.channel == ch,
                                onClick = { onSwitchOtaChannel(ch) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SystemBlue.copy(alpha = 0.24f),
                                    selectedLabelColor = LabelPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onCheckOtaUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = SystemBlue),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_ota_check_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify OTA", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onRollbackOtaBundle,
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, SubtleBorderWhite, RoundedCornerShape(14.dp))
                                .testTag("settings_ota_rollback_button")
                        ) {
                            Text("Rollback Baseline", color = LabelPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Push Notifications (FCM) & Category Controls
            item {
                SettingsCardContainer(
                    title = "Push Notifications (FCM) & Deep Links",
                    icon = Icons.Default.NotificationsActive,
                    iconTint = BeatifyAppleCrimson
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (notificationPermissionGranted) {
                                "POST_NOTIFICATIONS permission granted • MediaSession & FCM channels ready"
                            } else {
                                "Enable Android 13+ notification permission to receive release & podcast alerts"
                            },
                            color = if (notificationPermissionGranted) SystemGreen else SecondaryTextZinc,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (!notificationPermissionGranted) {
                            Button(
                                onClick = onRequestNotificationPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = BeatifyAppleCrimson),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Allow", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Subscribed FCM Categories (synced to users/{uid}/devices/android_primary):",
                        color = SecondaryTextZinc,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "music" to "Music Releases",
                            "podcasts" to "Podcasts",
                            "playlists" to "Playlists",
                            "updates" to "App Updates",
                            "account" to "Account & Security",
                            "system" to "System"
                        ).forEach { (catId, label) ->
                            val selected = catId in notificationCategories
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleNotificationCategory(catId) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BeatifyAppleCrimson.copy(alpha = 0.24f),
                                    selectedLabelColor = LabelPrimary
                                )
                            )
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
                                text = "Marketing & Promotional Offers",
                                color = LabelPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Opt-in only (disabled by default per Play policy)",
                                color = SecondaryTextZinc,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = marketingOptIn,
                            onCheckedChange = onToggleMarketingOptIn,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BeatifyAppleCrimson
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Test Foreground FCM Notification & Deep-Link Routing:",
                        color = SecondaryTextZinc,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onTriggerSampleNotification("music") },
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Music Alert", color = LabelPrimary, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onTriggerSampleNotification("podcasts") },
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Podcast Ep", color = LabelPrimary, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onTriggerSampleNotification("playlists") },
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Playlist Sync", color = LabelPrimary, fontSize = 11.sp)
                        }
                    }

                    if (pushNotifications.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        pushNotifications.take(3).forEach { notif ->
                            Surface(
                                color = ElevatedSurface.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onNavigateDeepLink(notif.deepLink) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = notif.title,
                                            color = LabelPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${notif.body} (${notif.deepLink})",
                                            color = SecondaryTextZinc,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Open",
                                        color = BeatifyAppleCrimson,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Audio Quality Preferences & Gapless Playback
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
                                    selectedColor = BeatifyAppleCrimson,
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
                                text = "Gapless Playback & Crossfade Sync",
                                color = LabelPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Seamless transitions synced to users/{uid}/preferences/app",
                                color = SecondaryTextZinc,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = gaplessPlaybackEnabled,
                            onCheckedChange = onToggleGaplessPlayback,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BeatifyAppleCrimson
                            ),
                            modifier = Modifier.testTag("settings_gapless_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Crossfade Duration (${if (playbackState.crossfadeDurationSec == 0) "Off" else "${playbackState.crossfadeDurationSec}s"}):",
                        color = SecondaryTextZinc,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "Off", 2 to "2s", 4 to "4s", 6 to "6s", 8 to "8s", 12 to "12s").forEach { (sec, label) ->
                            FilterChip(
                                selected = playbackState.crossfadeDurationSec == sec,
                                onClick = { onSetCrossfadeDurationSeconds(sec) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SystemBlue.copy(alpha = 0.24f),
                                    selectedLabelColor = LabelPrimary
                                ),
                                modifier = Modifier.testTag("settings_crossfade_${sec}s")
                            )
                        }
                    }
                }
            }

            // 5. Sleep Timer
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

            // 6. Library Rescan & Artwork Cache Maintenance
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

            // 7. Honest Content Protection (ContentBlockingService)
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
                                text = "${protectionReport.activeFilterCount} block rules • ${protectionReport.allowlistRuleCount} allowlist rules • ${protectionReport.blockedRequestsCount} blocked / ${protectionReport.allowedEssentialCount} allowed",
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

                    if (protectionReport.recentDiagnostics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val latest = protectionReport.recentDiagnostics.first()
                        Text(
                            text = "Last rule: [${latest.action}] ${latest.ruleId} (${latest.category}) on ${latest.normalizedHost}",
                            color = SystemBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

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

            // 8. About Beatify & GPL-3.0 / Third-Party Notices
            item {
                SettingsCardContainer(
                    title = "About Beatify & Open-Source Notices",
                    icon = Icons.Default.Info,
                    iconTint = BeatifyAppleCrimson
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SynapseNeonLogo(size = 44.dp)
                        Column {
                            Text(
                                text = "Beatify v1.0.0 (${otaManifest.bundleVersion})",
                                color = LabelPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Package: com.beatify.in • AndroidX Media3 ExoPlayer + MediaSessionService + Cloud Firestore",
                                color = SecondaryTextZinc,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Echo Music Reference & GPL-3.0 Attribution: Portions of the YouTube Music InnerTube protocol adapter are inspired by EchoMusicApp/Echo-Music (https://github.com/EchoMusicApp/Echo-Music), licensed under the GNU General Public License v3.0 (GPL-3.0). Third-party libraries include AndroidX Media3, Room, Jetpack Compose, OkHttp, Coil, and Firebase SDKs (Apache-2.0).",
                        color = SecondaryTextZinc,
                        fontSize = 11.sp
                    )
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
