package com.polarisrh.tabletpolaris.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** Encolhe levemente no toque (soltando de volta com uma pequena mola) — dá feedback tátil
 *  num botão/tecla que, com o ripple padrão do Material3 sozinho, ficava "seco" num tablet
 *  usado a dedo o dia inteiro. [interactionSource] precisa ser o MESMO passado pro componente
 *  clicável (Button/OutlinedButton `interactionSource = ...`), senão não pega o estado de
 *  pressionado de verdade. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
