package com.chb.form.ui

// =============================================================================
// WorkspaceEditor — reference shell (not the runtime implementation)
// =============================================================================
//
// This file exists for documentation and team orientation only.
// The real workspace implementation lives in:
//
//   app/src/main/java/com/chb/form/ui/NeoWorkspaceScreen.kt
//   app/src/main/java/com/chb/form/MainActivity.kt   (Screen.Workspace route)
//
// What this app now provides:
//
// 1. Magnetic touch
//    - Objects on the canvas snap to a magnetic grid when dragged near a point.
//    - Snap tolerance changes with work weight (Light / Medium / Heavy).
//    - Haptic feedback and a short visual pulse confirm the snap.
//
// 2. Elastic response
//    - Press, drag, and drop use spring animation.
//    - Three weight levels (50 / 75 / 100) control stiffness and damping.
//    - Heavy work can use a non-bouncy spring for firmer placement.
//
// 3. Separate management modes via toolbar carousel
//    - Palette, Geometry, Annotation, Styling, Export
//    - Modes are first-class screens; the user switches panels without leaving
//      the canvas and without relying on page scrolling to reach tools.
//
// 4. Single-user only
//    - No collaboration features are enabled in this app.
//
// 5. Where to read the actual code
//    - NeoWorkspaceScreen.kt  : canvas, objects, snap, spring helpers, modes
//    - MainActivity.kt        : route to Screen.Workspace
//    - GeoVocabRoutesTest.kt  : lightweight runtime validation of the snap model
//
// =============================================================================

// File roles in this workspace feature
//
// • NeoWorkspaceScreen.kt
//     - Primary runtime implementation.
//     - Contains CanvasState, Mobject, ToolMode, snapPosition,
//       animateSpringOffset, NeoSpringEasing, NeoWeight, NeoSnapGrid.
//     - Contains the toolbar carousel, magnetic canvas chip, and screen layout.
//
// • MainActivity.kt
//     - Routes the "workspace" screen to NeoWorkspaceScreen.
//
// • WorkspaceEditor.kt
//     - Reference file only.
//     - Do not treat this as the live implementation.
//
// • GeoVocabRoutesTest.kt
//     - Lightweight runtime validation helpers.
//     - Not a JUnit test; used to assert snap/weight rules when the workspace
//       is initialized.
//
// END
