package com.example.treksafetyapp.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

@Composable
fun TrackingScreen(viewModel: TrekViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    // --- State ---
    val durationStr           by viewModel.durationHours.collectAsState()
    val smsStatus             by viewModel.smsStatus.collectAsState()
    val networkStatus         by viewModel.networkStatus.collectAsState()
    val isGpsActive           by viewModel.isGpsEnabled.collectAsState()
    val isSmsPermission       by viewModel.isSmsPermissionGranted.collectAsState()
    val batteryLevel          by viewModel.batteryLevel.collectAsState()

    var timeLeftSeconds by remember {
        mutableStateOf((durationStr.toLongOrNull() ?: 2L) * 3600)
    }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (timeLeftSeconds > 0) {
            delay(1000)
            timeLeftSeconds--
        }
    }

    // Refresh on every app resume (instant permission detection) + every 15s as backup
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reValidateSystem()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.reValidateSystem()
            delay(15_000)  // secondary safety net
        }
    }

    // Track "last seen alive" time
    var lastSeenAlive by remember { mutableStateOf("just now") }
    LaunchedEffect(smsStatus, networkStatus) {
        lastSeenAlive = "just now"
    }

    // --- Stationary Grace Dialog (broadcast from service) ---
    val showStationaryDialog = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                showStationaryDialog.value = true
            }
        }
        context.registerReceiver(receiver, IntentFilter("com.example.treksafetyapp.STATIONARY_WARNING"))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- End Trek Confirmation Dialog ---
    val showEndTrekDialog = remember { mutableStateOf(false) }
    val showNoContactWarning = remember { mutableStateOf(false) }

    // --- Color palette ---
    val darkBlue      = Color(0xFF0B3C5D)
    val safetyGreen   = Color(0xFF2ECC71)
    val amber         = Color(0xFFFFC107)
    val errorRed      = MaterialTheme.colorScheme.error
    val surfaceAlpha  = Color.White.copy(alpha = 0.07f)

    // ══════════════════════════════════════════════════════════════════════
    // SMS Permission Block Overlay — full screen, immediate, no way around
    // ══════════════════════════════════════════════════════════════════════
    if (!isSmsPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A0000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⛔", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "SMS Permission Revoked",
                    color = Color.Red,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Emergency alerts CANNOT be sent.\nTracking is suspended until permission is restored.",
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = { viewModel.reValidateSystem() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("I've Restored Permission — Recheck", color = Color.White)
                }
            }
        }
        return
    }

    // ══════════════════════════════════════════════════════════════════════
    // Main Tracking UI
    // ══════════════════════════════════════════════════════════════════════
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBlue)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            "🛡️ Trek Active",
            style = MaterialTheme.typography.headlineSmall,
            color = safetyGreen,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Last update: $lastSeenAlive",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Countdown ──────────────────────────────────────────────────
        val minutesLeft = timeLeftSeconds / 60
        val secondsLeft = timeLeftSeconds % 60
        Text(
            text = String.format("%02d:%02d", minutesLeft, secondsLeft),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text("Until Dead-Man Switch", color = Color.LightGray, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(20.dp))

        // ── System Health Panel ────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceAlpha)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("System Health", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                HealthRow(
                    label  = "📍 GPS",
                    value  = if (isGpsActive) "Active" else "Lost",
                    ok     = isGpsActive
                )
                HealthRow(
                    label  = "📡 Network",
                    value  = if (networkStatus == "LIVE") "Live Tracking" else "Saved Offline",
                    ok     = networkStatus == "LIVE",
                    amberOk = true  // Offline is amber, not red
                )
                HealthRow(
                    label  = "📨 SMS",
                    value  = when (smsStatus) {
                        "SENT"         -> "Sent ✓"
                        "DELIVERED"    -> "Delivered ✓"
                        "SENDING"      -> "Sending…"
                        "FAILED"       -> "Failed (retrying)"
                        "PENDING_RETRY"-> "Queued — sends on signal"
                        "NOT_DELIVERED"-> "Not delivered"
                        else           -> "Ready"
                    },
                    ok = smsStatus in listOf("IDLE", "SENT", "DELIVERED", "SENDING"),
                    amberOk = smsStatus == "PENDING_RETRY"
                )
                HealthRow(
                    label  = "🔋 Battery",
                    value  = "$batteryLevel%  ${if (batteryLevel < 20) "⚠️ Survival Mode" else ""}",
                    ok     = batteryLevel >= 20,
                    amberOk = batteryLevel in 10..19
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Check-in Now ───────────────────────────────────────────────
        OutlinedButton(
            onClick  = { viewModel.triggerCheckIn() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = safetyGreen),
            border   = androidx.compose.foundation.BorderStroke(1.dp, safetyGreen)
        ) {
            Text("📡  Send Check-in Now", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── SOS Button ────────────────────────────────────────────────
        Button(
            onClick  = { viewModel.triggerSOS() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = errorRed),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Text("🚨 Send Emergency SOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── End Trek (2-step guard) ────────────────────────────────────
        Button(
            onClick = {
                if (!viewModel.hasContactBeenNotified()) {
                    showNoContactWarning.value = true
                } else {
                    showEndTrekDialog.value = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Text("End Trek", color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // ══════════════════════════════════════════════════════════════════════
    // Stationary Grace Dialog  ("Are you okay?")
    // ══════════════════════════════════════════════════════════════════════
    if (showStationaryDialog.value) {
        AlertDialog(
            onDismissRequest = {},  // force explicit choice
            title   = { Text("⚠️ No Movement Detected") },
            text    = { Text("No movement for over 30 minutes.\nAre you okay?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acknowledgeStationary()
                    showStationaryDialog.value = false
                }) {
                    Text("✅ I'm OK", color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Let the 2-minute countdown fire the alert
                    showStationaryDialog.value = false
                }) {
                    Text("Need Help", color = Color.Red)
                }
            }
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // End Trek Confirmation Dialog
    // ══════════════════════════════════════════════════════════════════════
    if (showEndTrekDialog.value) {
        AlertDialog(
            onDismissRequest = { showEndTrekDialog.value = false },
            title   = { Text("End Trek?") },
            text    = { Text("This will stop all tracking and safety alerts.\nYour emergency contact will be notified you are safe.") },
            confirmButton = {
                TextButton(onClick = {
                    showEndTrekDialog.value = false
                    viewModel.stopTrek()
                    onNavigateBack()
                }) {
                    Text("End Trek", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTrekDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // No Contact Warning Dialog (End Trek without notify guard)
    // ══════════════════════════════════════════════════════════════════════
    if (showNoContactWarning.value) {
        AlertDialog(
            onDismissRequest = { showNoContactWarning.value = false },
            title   = { Text("⚠️ Contact Not Alerted") },
            text    = { Text("Your emergency contact has not received any messages yet.\nEnding now means they won't know you started or finished.") },
            confirmButton = {
                TextButton(onClick = {
                    showNoContactWarning.value = false
                    viewModel.stopTrek()
                    onNavigateBack()
                }) {
                    Text("End Anyway", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoContactWarning.value = false }) {
                    Text("Stay in Trek")
                }
            }
        )
    }
}

// ── Reusable health row ────────────────────────────────────────────────────────
@Composable
private fun HealthRow(label: String, value: String, ok: Boolean, amberOk: Boolean = false) {
    val amber = Color(0xFFFFC107)
    val green = Color(0xFF2ECC71)
    val red   = Color(0xFFE53935)

    val color = when {
        ok      -> green
        amberOk -> amber
        else    -> red
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
