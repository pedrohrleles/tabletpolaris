package com.polarisrh.tabletpolaris.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val keypadRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("", "0", "⌫")
)

private val KeySize = 112.dp

@Composable
fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        keypadRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.size(KeySize))
                        "⌫" -> OutlinedButton(
                            onClick = onBackspace,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(KeySize)
                        ) {
                            Text(text = key, style = MaterialTheme.typography.headlineSmall)
                        }
                        else -> OutlinedButton(
                            onClick = { onDigit(key) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(KeySize)
                        ) {
                            Text(text = key, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}
