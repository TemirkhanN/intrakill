package me.nasukhov.intrakill.kmp

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class TextSizeByWeightScalerTest {
    @Test
    fun `unspecified font size is not allowed`() {
        assertFails("Scaling requires size range to be explicitly set") {
            TextSizeByWeightScaler(
                minFontSize = TextUnit.Unspecified,
                maxFontSize = 20.sp,
                minWeight = 1,
                maxWeight = 2,
            )
        }

        assertFails("Scaling requires size range to be explicitly set") {
            TextSizeByWeightScaler(
                minFontSize = 20.sp,
                maxFontSize = TextUnit.Unspecified,
                minWeight = 1,
                maxWeight = 2,
            )
        }

        assertFails("Font sizes must have same representation") {
            TextSizeByWeightScaler(
                minFontSize = 20.sp,
                maxFontSize = 30.em,
                minWeight = 1,
                maxWeight = 2,
            )
        }

        assertFails("Minimal font size must be smaller than maximum") {
            TextSizeByWeightScaler(
                minFontSize = 30.sp,
                maxFontSize = 20.sp,
                minWeight = 1,
                maxWeight = 2,
            )
        }
    }

    @Test
    fun `minimal weight matches minimal font size`() {
        val scaler =
            TextSizeByWeightScaler(
                minFontSize = 10.sp,
                maxFontSize = 20.sp,
                minWeight = 1,
                maxWeight = 200,
            )

        assertEquals(10.sp, scaler.getSize(1))

        val scalerEm =
            TextSizeByWeightScaler(
                minFontSize = 10.em,
                maxFontSize = 20.em,
                minWeight = 1,
                maxWeight = 200,
            )

        assertEquals(10.em, scalerEm.getSize(1))
    }

    @Test
    fun `max weight matches max font size`() {
        val scaler =
            TextSizeByWeightScaler(
                minFontSize = 10.sp,
                maxFontSize = 20.sp,
                minWeight = 1,
                maxWeight = 200,
            )

        assertEquals(20.sp, scaler.getSize(200))

        val scalerEm =
            TextSizeByWeightScaler(
                minFontSize = 10.em,
                maxFontSize = 20.em,
                minWeight = 1,
                maxWeight = 200,
            )

        assertEquals(20.em, scalerEm.getSize(200))
    }

    @Test
    fun `font size does not go beyond bounds`() {
        val scaler =
            TextSizeByWeightScaler(
                minFontSize = 10.sp,
                maxFontSize = 21.sp,
                minWeight = 13,
                maxWeight = 200,
            )

        assertEquals(10.sp, scaler.getSize(5))
        assertEquals(21.sp, scaler.getSize(350))
    }
}
