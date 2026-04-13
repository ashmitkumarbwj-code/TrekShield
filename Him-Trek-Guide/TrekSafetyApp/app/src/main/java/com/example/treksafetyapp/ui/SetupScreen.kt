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

        Spacer(modifier = Modifier.weight(1f))

    val showPermissionDialog = remember { mutableStateOf(false) }

        Button(
            onClick = {
                val permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                    // Skip dialog and start trek immediately
                    viewModel.startTrek()
                    Toast.makeText(context, "Tracking started. You'll be alerted before time expires.", Toast.LENGTH_LONG).show()
                    onNavigateToTracking()
                } else {
                    showPermissionDialog.value = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = safetyGreen)
        ) {
            Text("Start Trek", color = darkBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }

    if (showPermissionDialog.value) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog.value = false },
            title = { Text("Why TrekShield needs SMS permission", fontWeight = FontWeight.Bold) },
            text = { Text("If you don’t check in on time, TrekShield will automatically send your live location to your emergency contact using SMS.\n\nThis helps them find you quickly in an emergency — even without internet.\n\nWe DO NOT read your personal messages or send anything without this safety trigger.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog.value = false
                    Toast.makeText(context, "Permission required for emergency SMS alerts", Toast.LENGTH_SHORT).show()
                    permissionLauncher.launch(Manifest.permission.SEND_SMS)
                }) {
                    Text("Continue", color = safetyGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog.value = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = darkBlue,
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }
}
