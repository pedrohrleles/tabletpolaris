package com.polarisrh.tabletpolaris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.polarisrh.tabletpolaris.ui.theme.PolarisBlue
import com.polarisrh.tabletpolaris.ui.theme.PolarisCard

private val CardRadius = 32.dp
private val StripeHeight = 8.dp

/** Cartão claro com uma listra colorida no topo, igual ao card de login do web — como o fundo
 *  do app aqui já é azul escuro (diferente do web, que é branco), a listra usa um azul mais
 *  claro (accent) pra não se confundir com o fundo. Um único `clip` no Column externo arredonda
 *  a listra (topo) e o corpo do card (base) como se fossem uma peça só. */
@Composable
fun PolarisStripedCard(
    modifier: Modifier = Modifier,
    stripeColor: Color = PolarisBlue,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.clip(RoundedCornerShape(CardRadius))) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StripeHeight)
                .background(stripeColor)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PolarisCard)
                .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}
