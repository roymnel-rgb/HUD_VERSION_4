
package com.example.hud_version_4

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
fun AnalogGauge(
    value: Float?,
    maxValue: Float,
    majorStep: Int,
    title: String,
    unit: String,
    modifier: Modifier = Modifier,
    redlineStart: Float? = null,
    numberDivisor: Int = 1,
    illumination: Color = Color.White
) {

    val context = LocalContext.current

    val orbitron = remember(context) {
        ResourcesCompat.getFont(
            context,
            R.font.orbitron
        ) ?: Typeface.DEFAULT_BOLD
    }

    val animatedValue by animateFloatAsState(
        targetValue = (value ?: 0f)
            .coerceIn(0f, maxValue),
        animationSpec = tween(350),
        label = "GaugeNeedle"
    )

    Canvas(modifier = modifier) {

        val radius = size.minDimension / 2f
        if (radius <= 0f || maxValue <= 0f || majorStep <= 0) return@Canvas
        val center = Offset(
            size.width / 2f,
            size.height / 2f
        )

        val cx = center.x
        val cy = center.y

        val startAngle = 135f
        val sweepAngle = 270f

        val flatBottom = cy + radius * 0.79f
        clipRect(bottom = flatBottom) {
        // Metallic outer bezel
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFE0E0E0),
                    Color(0xFF777A80),
                    Color(0xFF25272B),
                    Color(0xFFBFC2C8)
                ),
                start = Offset(
                    cx - radius,
                    cy - radius
                ),
                end = Offset(
                    cx + radius,
                    cy + radius
                )
            ),
            radius = radius * 0.98f,
            center = center
        )

        // Inner bezel
        drawCircle(
            color = Color(0xFF050505),
            radius = radius * 0.93f,
            center = center
        )

        // Instrument face
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF28292C),
                    Color(0xFF08090B)
                ),
                center = center,
                radius = radius
            ),
            radius = radius * 0.90f,
            center = center
        )

        // Subtle illuminated ring
        drawCircle(
            color = illumination.copy(alpha = 0.35f),
            radius = radius * 0.88f,
            center = center,
            style = Stroke(radius * 0.008f)
        )

        fun point(
            angle: Float,
            distance: Float
        ): Offset {

            val radians = Math.toRadians(
                angle.toDouble()
            )

            return Offset(
                cx + cos(radians).toFloat() * distance,
                cy + sin(radians).toFloat() * distance
            )
        }

        val majorCount =
            (maxValue / majorStep).roundToInt()

        val totalTicks = majorCount * 10

        // Major and minor tick marks
        for (i in 0..totalTicks) {

            val fraction =
                i.toFloat() / totalTicks

            val angle =
                startAngle + sweepAngle * fraction

            val tickValue = maxValue * fraction

            val major = i % 10 == 0
            val middle = i % 5 == 0

            val outer = radius * 0.85f

            val inner = when {
                major -> radius * 0.71f
                middle -> radius * 0.76f
                else -> radius * 0.80f
            }

            val tickColor =
                if (
                    redlineStart != null &&
                    tickValue >= redlineStart
                ) {
                    Color(0xFFFF3932)
                } else {
                    illumination
                }

            drawLine(
                color = tickColor,
                start = point(angle, inner),
                end = point(angle, outer),
                strokeWidth = radius * when {
                    major -> 0.027f
                    middle -> 0.016f
                    else -> 0.010f
                }
            )
        }

        // Tachometer redline
        if (redlineStart != null) {

            val redFraction =
                (redlineStart / maxValue)
                    .coerceIn(0f, 1f)

            val arcRadius = radius * 0.87f

            drawArc(
                color = Color(0xFFCC2525),
                startAngle =
                    startAngle +
                            sweepAngle * redFraction,
                sweepAngle =
                    sweepAngle * (1f - redFraction),
                useCenter = false,
                topLeft = Offset(
                    cx - arcRadius,
                    cy - arcRadius
                ),
                size = Size(
                    arcRadius * 2f,
                    arcRadius * 2f
                ),
                style = Stroke(radius * 0.025f)
            )
        }

        val textPaint = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            typeface = orbitron
            color = illumination.toArgb()
            textAlign = Paint.Align.CENTER
        }

        fun drawCenteredText(
            text: String,
            x: Float,
            y: Float,
            textSize: Float,
            color: Color
        ) {

            textPaint.textSize = textSize
            textPaint.color = color.toArgb()

            val baseline =
                y - (
                        textPaint.ascent() +
                                textPaint.descent()
                        ) / 2f

            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    text,
                    x,
                    baseline,
                    textPaint
                )
            }
        }

        // Orbitron dial numerals
        for (i in 0..majorCount) {

            val fraction =
                i.toFloat() / majorCount

            val angle =
                startAngle + sweepAngle * fraction

            val position =
                point(angle, radius * 0.59f)

            val number =
                (i * majorStep) / numberDivisor

            drawCenteredText(
                text = number.toString(),
                x = position.x,
                y = position.y,
                textSize = radius * 0.145f,
                color = illumination
            )
        }

        drawCenteredText(
            text = if (numberDivisor == 1000) "$title ×1000" else title,
            x = cx,
            y = cy - radius * 0.28f,
            textSize = radius * 0.10f,
            color = Color.LightGray
        )

        // Needle rotation
        val needleAngle =
            startAngle +
                    sweepAngle *
                    (animatedValue / maxValue)

        // Keep the needle parked at zero when data is unavailable.
        if (maxValue > 0f) {

            rotate(
                degrees = needleAngle + 90f,
                pivot = center
            ) {

                // Full needle assembly,
                // including the counterweight tail.
                val needleBody = Path().apply {

                    moveTo(
                        cx - radius * 0.045f,
                        cy + radius * 0.27f
                    )

                    lineTo(
                        cx - radius * 0.022f,
                        cy - radius * 0.80f
                    )

                    lineTo(
                        cx + radius * 0.022f,
                        cy - radius * 0.80f
                    )

                    lineTo(
                        cx + radius * 0.045f,
                        cy + radius * 0.27f
                    )

                    close()
                }

                drawPath(
                    path = needleBody,
                    color = Color(0xFF44464B)
                )

                // Red forward-facing pointer
                val redPointer = Path().apply {

                    moveTo(
                        cx - radius * 0.018f,
                        cy - radius * 0.035f
                    )

                    lineTo(
                        cx - radius * 0.012f,
                        cy - radius * 0.79f
                    )

                    lineTo(
                        cx + radius * 0.012f,
                        cy - radius * 0.79f
                    )

                    lineTo(
                        cx + radius * 0.018f,
                        cy - radius * 0.035f
                    )

                    close()
                }

                drawPath(
                    path = redPointer,
                    color = Color(0xFFFF3028)
                )
            }
        }

        // Central needle hub
        drawCircle(
            color = Color(0xFF111111),
            radius = radius * 0.115f,
            center = center
        )

        drawCircle(
            color = Color(0xFF444444),
            radius = radius * 0.095f,
            center = center,
            style = Stroke(radius * 0.015f)
        )

        drawCircle(
            color = Color(0xFF252629),
            radius = radius * 0.075f,
            center = center
        )

        // Recessed digital display, inside the flattened bezel.
        drawRoundRect(color = Color(0xFF050505),
            topLeft = Offset(cx - radius * 0.34f, cy + radius * 0.32f),
            size = Size(radius * 0.68f, radius * 0.39f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.04f))
        drawCenteredText(value?.roundToInt()?.toString() ?: "--", cx,
            cy + radius * 0.46f, radius * 0.22f, illumination)
        drawCenteredText(unit, cx, cy + radius * 0.63f, radius * 0.09f, Color.LightGray)

        }
        val halfChord = radius * kotlin.math.sqrt(0.98f * 0.98f - 0.79f * 0.79f)
        drawLine(
            color = Color(0xFFBFC2C8),
            start = Offset(cx - halfChord, flatBottom),
            end = Offset(cx + halfChord, flatBottom),
            strokeWidth = radius * 0.025f
        )
    }
}
