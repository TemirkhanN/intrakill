package me.nasukhov.intrakill.ui.view

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun NotFound() =
    Text(
        "¯\\_(ツ)_/¯",
        textAlign = TextAlign.Center,
        color = Color.Gray,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        maxLines = 1,
    )
