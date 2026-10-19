package com.chb.form.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class NeoWorkspaceSnapTest {

    @Test
    fun weightValuesAreFixed() {
        assertEquals(50f, NeoWeight.Light.value, 0f)
        assertEquals(75f, NeoWeight.Medium.value, 0f)
        assertEquals(100f, NeoWeight.Heavy.value, 0f)
    }

    @Test
    fun gridPointStaysPut() {
        assertEquals(Offset(0f, 0f), snapPosition(0f, 0f, NeoWeight.Heavy))
        assertEquals(Offset(48f, 48f), snapPosition(48f, 48f, NeoWeight.Light))
    }

    @Test
    fun nearGridPointSnapsForHeavyWeight() {
        assertEquals(Offset(24f, 24f), snapPosition(26f, 24f, NeoWeight.Heavy))
    }

    @Test
    fun pointBetweenCellsIsNotSnapped() {
        assertEquals(Offset(70f, 70f), snapPosition(70f, 70f, NeoWeight.Light))
        assertEquals(Offset(70f, 70f), snapPosition(70f, 70f, NeoWeight.Medium))
    }

    @Test
    fun heavyWeightSnapsWhereLighterWeightsDoNot() {
        // Nearest grid point is (48, 48), 22 px away on each axis.
        // Heavy threshold is 24 px, so it snaps; Light (12) and Medium (18) do not.
        assertEquals(Offset(48f, 48f), snapPosition(70f, 70f, NeoWeight.Heavy))
    }
}
