package com.example.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BeatifySyncStatus
import com.example.ui.theme.BeatifyAppleCrimson
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.MainSurface
import com.example.ui.theme.OledBlack
import com.example.ui.theme.SecondaryTextZinc
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SpecularBorderLight
import com.example.ui.theme.SubtleBorderWhite
import com.example.ui.theme.SystemBlue
import com.example.ui.theme.SystemGreen

/**
 * Full-screen or modal Beatify Google Sign-In & Cloud Account Sheet.
 *
 * Implements the requirements from Section 1 of the Beatify Authentication prompt:
 * - Beatify logo and wordmark
 * - Welcome headline & account synchronization summary
 * - Continue with Google button (CredentialManager -> Firebase Auth)
 * - Loading indicator & clear error messages
 * - Privacy policy & terms disclosure
 * - Continue without an account option for local music playback
 */
@Composable
fun BeatifyAuthOverlay(
    currentUserUid: String?,
    currentUserEmail: String?,
    currentUserDisplayName: String?,
    isAuthenticating: Boolean,
    authErrorMessage: String?,
    syncStatus: BeatifySyncStatus,
    onContinueWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onTriggerManualSync: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack.copy(alpha = 0.96f))
            .clickable(enabled = true, onClick = {})
            .testTag("beatify_auth_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MainSurface,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .border(1.dp, SpecularBorderLight, RoundedCornerShape(28.dp))
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BeatifyAppleCrimson.copy(alpha = 0.18f),
                                SecondaryViolet.copy(alpha = 0.10f),
                                MainSurface
                            )
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SynapseNeonLogo(size = 36.dp)
                        Text(
                            text = "Beatify",
                            color = LabelPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = onContinueWithoutAccount,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ElevatedSurface)
                            .testTag("auth_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Account Sheet",
                            tint = LabelPrimary
                        )
                    }
                }

                if (currentUserUid == null) {
                    // Signed-out Google Sign-In state
                    SynapseNeonLogo(size = 68.dp)

                    Text(
                        text = "Sync Your Music Everywhere",
                        color = LabelPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Sign in with Google to synchronize your playlists, favorite tracks, radio & podcast subscriptions, and playback settings across devices with Cloud Firestore.",
                        color = SecondaryTextZinc,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    // Feature bullets
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ElevatedSurface.copy(alpha = 0.7f))
                            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuthFeatureRow(
                            icon = Icons.Default.CloudSync,
                            tint = BeatifyAppleCrimson,
                            text = "Real-time playlists, favorites & revision conflict safety"
                        )
                        AuthFeatureRow(
                            icon = Icons.Default.NotificationsActive,
                            tint = SystemBlue,
                            text = "Podcast & release notifications via Firebase Cloud Messaging"
                        )
                        AuthFeatureRow(
                            icon = Icons.Default.Security,
                            tint = SystemGreen,
                            text = "Zero-trust per-user Firestore rules (users/{uid})"
                        )
                    }

                    if (!authErrorMessage.isNullOrBlank()) {
                        Surface(
                            color = BeatifyAppleCrimson.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BeatifyAppleCrimson.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = BeatifyAppleCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = authErrorMessage,
                                    color = LabelPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onContinueWithGoogle,
                        enabled = !isAuthenticating,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F1F1F)
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("continue_with_google_button")
                    ) {
                        if (isAuthenticating) {
                            CircularProgressIndicator(
                                color = Color(0xFF1F1F1F),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Authenticating with Google...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = SystemBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                color = Color(0xFF1F1F1F),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onContinueWithoutAccount,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("continue_without_account_button")
                    ) {
                        Text(
                            text = "Continue without an account (Local Playback)",
                            color = LabelPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "By continuing, you agree to Beatify's Terms of Service & Privacy Policy. Google credentials are verified via Firebase Authentication.",
                        color = SecondaryTextZinc,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Signed-in Profile & Cloud Sync Summary
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(BeatifyAppleCrimson.copy(alpha = 0.22f))
                            .border(1.5.dp, BeatifyAppleCrimson, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = SystemGreen,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = currentUserDisplayName?.ifBlank { "Beatify Listener" } ?: "Beatify Listener",
                        color = LabelPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = currentUserEmail ?: "UID: ${currentUserUid.take(12)}...",
                        color = SecondaryTextZinc,
                        fontSize = 13.sp
                    )

                    val syncBadgeText = when (syncStatus) {
                        is BeatifySyncStatus.Synced -> "Cloud Firestore Synced • ${syncStatus.pendingOutboxCount} queued"
                        is BeatifySyncStatus.Loading -> "Synchronizing with Cloud Firestore..."
                        is BeatifySyncStatus.Offline -> "Offline Mode • ${syncStatus.pendingOutboxCount} changes queued"
                        is BeatifySyncStatus.Error -> "Sync notice: ${syncStatus.message}"
                        is BeatifySyncStatus.SignedOut -> "Signed out"
                    }

                    Surface(
                        color = ElevatedSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SubtleBorderWhite, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SystemGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = syncBadgeText,
                                color = LabelPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onTriggerManualSync,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SystemBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Now", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BeatifyAppleCrimson),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = LabelPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
