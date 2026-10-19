package com.chb.form.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset

// -----------------------------------------------------------------------------
// Lightweight runtime validation used to prove the snap + weight model works
// -----------------------------------------------------------------------------

@Composable
fun validateWorkspaceSnapModel() {
    // First pass: pick any point and ensure it snaps inside one of the grid cells
    val origin = Offset(0f, 0f)
    val snapped = snapPosition(origin.x, origin.y, NeoWeight.Heavy)
    kotlin.run {
        assert(snapped == origin) { "expected origin to snap to itself, got $snapped" }
    }

    // Heavy weight should accept coordinates slightly off-grid
    val near = Offset(24f + 2f, 24f)
    val snappedNear = snapPosition(near.x, near.y, NeoWeight.Heavy)
    kotlin.run {
        assert(snappedNear == Offset(24f, 24f)) { "expected near point to snap to grid, got $snappedNear" }
    }

    // Light weight should not snap as far
    val lightNear = snapPosition(24f + 10f, 24f, NeoWeight.Light)
    kotlin.run {
        assert(lightNear == Offset(34f, 24f)) { "expected light weight to stay put when too far, got $lightNear" }
    }

    // Weight bounds
    kotlin.run {
        assert(NeoWeight.Light.value == 50f)
        assert(NeoWeight.Medium.value == 75f)
        assert(NeoWeight.Heavy.value == 100f)
    }

    // Spring configuration by weight
    kotlin.run {
        val low: SpringSpec<Float> = NeoSpringEasing(50f)
        val high: SpringSpec<Float> = NeoSpringEasing(100f)
        assert(high.dampingRatio == Spring.DampingRatioNoBouncy)
        assert(low.dampingRatio == Spring.DampingRatioMediumBouncy)
    }
}

// -----------------------------------------------------------------------------
// Paper-space pivot initialization guard
// -----------------------------------------------------------------------------
// Keeps the paper pivot from being drawn unless later explicitly drafted.

interface PaperPivotState {
    val pivotX: Float
    val pivotY: Float
    val scale: Float
    val rotation: Float
}

@Composable
fun rememberPaperPivot(): PaperPivotState {
    val pivotX = remember { mutableFloatStateOf(0f) }
    val pivotY = remember { mutableFloatStateOf(0f) }
    val scale = remember { mutableFloatStateOf(1f) }
    val rotation = remember { mutableFloatStateOf(0f) }
    return remember {
        object : PaperPivotState {
            override val pivotX: Float get() = pivotX.floatValue
            override val pivotY: Float get() = pivotY.floatValue
            override val scale: Float get() = scale.floatValue
            override val rotation: Float get() = rotation.floatValue
        }
    }
}
