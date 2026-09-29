package me.nasukhov.intrakill.kmp

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified

data class TextSizeByWeightScaler(
    private val minFontSize: TextUnit,
    private val maxFontSize: TextUnit,
    private val minWeight: Int,
    private val maxWeight: Int,
) {
    private val sizeStep: Float
    private val textUnitType: TextUnitType

    init {
        check(minFontSize.isSpecified && maxFontSize.isSpecified) {
            "Scaling requires size range to be explicitly set"
        }
        check(minFontSize.isEm == maxFontSize.isEm) { "Font sizes must have same representation" }
        check(minFontSize < maxFontSize) { "Minimal font size must be smaller than maximum" }

        val weightGap = maxWeight - minWeight
        val fontSizeGap = maxFontSize.value - minFontSize.value
        sizeStep = if (weightGap > 0) fontSizeGap / weightGap.toFloat() else 0f
        textUnitType = if (minFontSize.isEm) TextUnitType.Em else TextUnitType.Sp
    }

    fun getSize(forWeight: Int): TextUnit {
        // Avoid moving beyond weights bounds
        val weight =
            if (forWeight < minWeight) {
                minWeight
            } else if (forWeight > maxWeight) {
                maxWeight
            } else {
                forWeight
            }

        val fontSize = ((weight - minWeight) * sizeStep) + minFontSize.value

        return TextUnit(fontSize, textUnitType)
    }
}
