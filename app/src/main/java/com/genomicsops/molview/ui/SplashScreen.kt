package com.genomicsops.molview.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genomicsops.molview.R
import kotlin.math.sin

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "phase"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onFinished()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(GenomicsDark),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "GenomicsOps Logo",
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(32.dp))
        Canvas(modifier = Modifier.size(64.dp)) {
            val centerX = size.width / 2
            val amplitude = size.width * 0.35f
            for (i in 0..8) {
                val y = i * (size.height / 8f)
                val x1 = centerX + amplitude * sin(phase + i * 0.6f)
                val x2 = centerX - amplitude * sin(phase + i * 0.6f)
                drawCircle(GenomicsCyan, radius = 4f, center = Offset(x1, y))
                drawCircle(GenomicsTeal, radius = 4f, center = Offset(x2, y))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("GenomicsOps", color = GenomicsText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("MolView", color = GenomicsCyan, fontSize = 14.sp)
    }
}
