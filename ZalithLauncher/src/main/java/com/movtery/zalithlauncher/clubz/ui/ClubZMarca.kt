package com.movtery.zalithlauncher.clubz.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R

/** Marca de la barra superior: ícono + CLUB Z + TEMPORADA 3 (como el header del launcher de PC). */
@Composable
fun ClubZMarca(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.clubz_icono), null, modifier = Modifier.size(24.dp))
        Text("CLUB Z", color = CZ.texto, style = TextStyle(fontFamily = CZ.pixel, fontSize = 14.sp, letterSpacing = 1.6.sp))
        Text(
            "TEMPORADA 3",
            color = CZ.sobreAcento,
            style = TextStyle(fontFamily = CZ.pixel, fontSize = 9.sp, letterSpacing = 0.9.sp),
            modifier = Modifier.clip(pixelShape(2.dp)).background(CZ.acento).padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
