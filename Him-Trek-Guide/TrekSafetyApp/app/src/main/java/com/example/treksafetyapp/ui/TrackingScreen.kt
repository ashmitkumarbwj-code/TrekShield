package com.example.treksafetyapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun TrackingScreen(viewModel: TrekViewModel, onNavigateBack: () -> Unit) {
    val durationStr by viewModel.durationHours.collectAsState()
    var timeLeftSeconds by remember { 
        mutableStateOf((durationStr.toLongOrNull() ?: 2L) * 60 * 60)
    }

    LaunchedEffect(Unit) {
        while (timeLeftSeconds > 0) {
            delay(1000)
            timeLeftSeconds--
        }
    }

    val darkBlue = Color(0xFF0B3C5D)
    val safetyGreen = Color(0xFF2ECC71)
    
    val minutesLeft = timeLeftSeconds / 60
    val secondsLeft = timeLeftSeconds % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBlue)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tracking Active", style = MaterialTheme.typography.headlineMedium, color = safetyGreen, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = String.format("%02d:%02d", minutesLeft, secondsLeft),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text("Remaining until Dead-Man Switch", color = Color.LightGray)

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = {
                viewModel.stopTrek()
                onNavigateBack()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = safetyGreen)
        ) {
            Text("I am Safe (Check-in)", color = darkBlue, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.triggerSOS() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Send Emergency SMS", color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        Text(
            text = "This will send your location to your emergency contact.",
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "🔋 Low battery usage. Runs efficiently in background.",
            color = safetyGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
