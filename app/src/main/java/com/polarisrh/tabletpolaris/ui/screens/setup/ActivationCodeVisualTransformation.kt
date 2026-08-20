package com.polarisrh.tabletpolaris.ui.screens.setup

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Displays a 12-char activation code grouped as XXXX-XXXX-XXXX (e.g. ABCD-3JFC-2J2D). */
class ActivationCodeVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.take(ACTIVATION_CODE_LENGTH)
        val formatted = buildString {
            raw.forEachIndexed { index, char ->
                if (index != 0 && index % 4 == 0) append('-')
                append(char)
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 4 -> offset
                offset <= 8 -> offset + 1
                else -> offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 4 -> offset
                offset <= 9 -> offset - 1
                else -> offset - 2
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
