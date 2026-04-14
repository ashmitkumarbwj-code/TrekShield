package com.example.treksafetyapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: TrekViewModel, onNavigateToTracking: () -> Unit) {
    val name by viewModel.userName.collectAsState()
    val contact by viewModel.contact.collectAsState()
    val duration by viewModel.durationHours.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startTrek()
            Toast.makeText(context, "Tracking started. You'll be alerted before time expires.", Toast.LENGTH_LONG).show()
            onNavigateToTracking()
        } else {
            Toast.makeText(context, "SMS permission is required for emergency alerts to work", Toast.LENGTH_LONG).show()
        }
    }

    val darkBlue = Color(0xFF0B3C5D)
    val safetyGreen = Color(0xFF2ECC71)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBlue)
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            "TrekShield Setup",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This app assists in emergency situations but does not guarantee rescue. Always inform local authorities.",
            color = Color.LightGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { viewModel.updateName(it) },
            label = { Text("Your Name (For SMS)", color = Color.White) }, 
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = safetyGreen,
                unfocusedBorderColor = Color.White,
                textColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = contact,
            onValueChange = { viewModel.updateContact(it) },
            label = { Text("Emergency Contact (person to alert)", color = Color.White) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = safetyGreen,
                unfocusedBorderColor = Color.White,
                textColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Enter the phone number of someone who should be alerted if you don't check in.\nWe only use SMS to alert your emergency contact if you don't check in.",
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = duration,
            onValueChange = { viewModel.updateDuration(it) },
            label = { Text("Trek Duration (Hours)", color = Color.White) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = safetyGreen,
                unfocusedBorderColor = Color.White,
                textColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Set how long you expect your trek to last.",
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Before your next trek:",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "Open TrekShield and tap \"Start Trek\"",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (contact.isNotBlank()) {
                            viewModel.sendTestSms()
                            Toast.makeText(context, "Test SMS sent successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter an emergency contact first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, safetyGreen)
                ) {
                    Text("Test Emergency SMS", color = safetyGreen, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Standard SMS charges may apply",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }

        val isGpsActive by viewModel.isGpsEnabled.collectAsState()
        val isSmsReady by viewModel.isSmsPermissionGranted.collectAsState()
        val batteryLevel by viewModel.batteryLevel.collectAsState()
        
        val showBatteryWarning = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            viewModel.reValidateSystem()
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System Health Check", fontWeight = FontWeight.Bold, color = Color.White)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // GPS Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val gpsColor = if (isGpsActive) safetyGreen else Color.Red
                    val gpsIcon = if (isGpsActive) "✅" else "❌"
                    Text("$gpsIcon GPS Signal", color = gpsColor, fontSize = 14.sp)
                    if (!isGpsActive) {
                        Text(" - Turn on GPS", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SMS Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val smsColor = if (isSmsReady) safetyGreen else Color.Red
                    val smsIcon = if (isSmsReady) "✅" else "❌"
                    Text("$smsIcon SMS Permissions", color = smsColor, fontSize = 14.sp)
                    if (!isSmsReady) {
                        Text(" - Tap 'Start' to fix", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Battery Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val batColor = if (batteryLevel >= 15) safetyGreen else Color(0xFFFFC107) // Yellow
                    val batIcon = if (batteryLevel >= 15) "✅" else "⚠️"
                    Text("$batIcon Battery: $batteryLevel%", color = batColor, fontSize = 14.sp)
                }
                   Spacer(modifier = Modifier.height(24.dp))

        val showPermissionDialog = remember { mutableStateOf(false) }

        Button(
            onClick = {
                if (batteryLevel < 15) {
                    showBatteryWarning.value = true
                } else {
                    if (isSmsReady) {
                        viewModel.startTrek()
                        onNavigateToTracking()
                    } else {
                        showPermissionDialog.value = true
                    }
                }
            },
            enabled = isGpsActive,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGpsActive) safetyGreen else Color.Gray
            )
        ) {
            Text(
                "Start Trek",
                color = if (isGpsActive) darkBlue else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        // Low battery warning dialog
        if (showBatteryWarning.value) {
            AlertDialog(
                onDismissRequest = { showBatteryWarning.value = false },
                title   = { Text("⚠️ Low Battery ($batteryLevel%)") },
                text    = { Text("TrekShield tracking may not reach its end if battery dies. Continue anyway?") },
                confirmButton = {
                    TextButton(onClick = {
                        showBatteryWarning.value = false
                        if (isSmsReady) {
                            viewModel.startTrek()
                            onNavigateToTracking()
                        } else {
                            showPermissionDialog.value = true
                        }
                    }) {
                        Text("Start Anyway", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatteryWarning.value = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // SMS permission explanation dialog
        if (showPermissionDialog.value) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog.value = false },
                title = { Text("Why TrekShield Needs SMS", fontWeight = FontWeight.Bold) },
                text  = {
                    Text(
                        "TrekShield uses SMS to alert your emergency contact if you don't check in on time " +
                        "— even without internet.\n\nWe never read your messages or send anything without a safety trigger."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPermissionDialog.value = false
                        permissionLauncher.launch(Manifest.permission.SEND_SMS)
                    }) {
                        Text("Grant Permission", color = safetyGreen, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog.value = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor    = darkBlue,
                titleContentColor = Color.White,
                textContentColor  = Color.LightGray
            )
        }
    }
}
