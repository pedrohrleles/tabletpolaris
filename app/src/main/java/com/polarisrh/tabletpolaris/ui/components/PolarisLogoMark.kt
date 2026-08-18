package com.polarisrh.tabletpolaris.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.polarisrh.tabletpolaris.R

@Composable
fun PolarisLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    Image(
        painter = painterResource(id = R.drawable.logo_polaris),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
