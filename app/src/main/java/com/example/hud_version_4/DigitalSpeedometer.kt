
package com.example.hud_version_4

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal val LocalDigitalSpeedWidth = staticCompositionLocalOf<Dp?> { null }

private val segmentPatterns = mapOf(
    '0' to listOf(0, 1, 2, 3, 4, 5),
    '1' to listOf(1, 2),
    '2' to listOf(0, 1, 6, 4, 3),
    '3' to listOf(0, 1, 2, 3, 6),
    '4' to listOf(5, 6, 1, 2),
    '5' to listOf(0, 5, 6, 2, 3),
    '6' to listOf(5, 4, 3, 2, 6),
    '7' to listOf(0, 1, 2),
    '8' to listOf(0, 1, 2, 3, 4, 5, 6),
    '9' to listOf(0, 1, 2, 5, 6),
    '-' to listOf(6),
    ' ' to emptyList<Int>()
)

@Composable
fun DigitalSpeedometer(
    speed: Float?,
    unit: String,
    modifier: Modifier = Modifier,
    reserveThreeDigits: Boolean = false
) {

    val referenceWidth = LocalDigitalSpeedWidth.current

    val display = speed
        ?.roundToInt()
        ?.coerceIn(0, 999)
        ?.toString()
        ?.padStart(3, ' ')
        ?: "---"

    val orbitron = FontFamily(
        Font(R.font.orbitron)
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {

            // Fit the actual digits to the available rectangular canvas.
            // No circular arc or radius-based sizing.
            val visible = display.trimStart()
            val count = visible.length.coerceAtLeast(1)
            // All modes use the DRIVE panel width and reserve three digits.
            // Local bounds remain a safety limit on compact screens.
            val gapRatio = 0.16f
            val aspect = 1.8f
            val widthLimit = minOf(size.width, referenceWidth?.toPx() ?: size.width)
            val digitWidth = minOf(
                widthLimit * 0.88f / (3f + 2f * gapRatio),
                size.height * 0.90f / aspect
            )
            val digitHeight = digitWidth * aspect
            val spacing = digitWidth * gapRatio
            val totalWidth = digitWidth * count + spacing * (count - 1)
            val startX = (size.width - totalWidth) / 2f
            val startY = (size.height - digitHeight) / 2f

            val segments = listOf(
                floatArrayOf(0.15f, 0.06f, 0.85f, 0.06f),
                floatArrayOf(0.92f, 0.12f, 0.92f, 0.44f),
                floatArrayOf(0.92f, 0.56f, 0.92f, 0.88f),
                floatArrayOf(0.15f, 0.94f, 0.85f, 0.94f),
                floatArrayOf(0.08f, 0.56f, 0.08f, 0.88f),
                floatArrayOf(0.08f, 0.12f, 0.08f, 0.44f),
                floatArrayOf(0.15f, 0.50f, 0.85f, 0.50f)
            )

            visible.forEachIndexed { index, digit ->

                val active =
                    segmentPatterns[digit] ?: emptyList()

                val x = startX +
                        index * (digitWidth + spacing)

                segments.forEachIndexed { s, p ->

                    drawLine(
                        color = if (s in active) {
                            Color(0xFFFF3028)
                        } else {
                            Color(0xFF481010)
                        },
                        start = Offset(
                            x + p[0] * digitWidth,
                            startY + p[1] * digitHeight
                        ),
                        end = Offset(
                            x + p[2] * digitWidth,
                            startY + p[3] * digitHeight
                        ),
                        strokeWidth = digitWidth * 0.12f,
                        cap = StrokeCap.Butt
                    )
                }
            }
        }

        Text(
            text = unit,
            color = Color(0xFFFF3535),
            fontFamily = orbitron,
            fontSize = 22.sp
        )

        Spacer(Modifier.height(6.dp))
    }
}
