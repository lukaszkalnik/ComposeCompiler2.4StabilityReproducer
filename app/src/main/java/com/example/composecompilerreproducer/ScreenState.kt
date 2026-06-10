package com.example.composecompilerreproducer

import androidx.compose.runtime.Immutable

// UI state holder, in its OWN file, `internal`, annotated @Immutable exactly like the real
// CartWithItemsScreenState.
//
// TOGGLE FOR THE FIX: removing @Immutable here ALSO fixes the 2.4.0 build, because the holder
// then compares by structural equality and Products recomposes with the new list.
@Immutable
internal data class ScreenState(
    val items: List<ProductItem>,
    val total: Int,
)

