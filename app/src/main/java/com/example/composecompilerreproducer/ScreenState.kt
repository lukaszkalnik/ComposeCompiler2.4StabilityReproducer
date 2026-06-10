package com.example.composecompilerreproducer

import androidx.compose.runtime.Immutable

// Mirror the real SDK: the collected state is a SEALED BASE type (`CartScreenState`), and the
// items-bearing state (`ScreenState`, like `CartWithItemsScreenState`) is one subclass annotated
// @Immutable. The UI collects the sealed base type and `when`-dispatches. The original
// reproducer collected the concrete @Immutable type directly, skipping this dispatch boundary.
internal sealed class CartScreenState {

    abstract val total: Int
}

internal data class EmptyCartScreenState(
    override val total: Int = 0,
) : CartScreenState()

// TOGGLE FOR THE FIX: removing @Immutable here fixes the 2.4.0 build, because the holder
// then compares by structural equality and Products recomposes with the new list. (This is the
// fix that works — annotating the LEAF ProductItem with @Immutable does NOT fix it.)
@Immutable
internal data class ScreenState(
    val items: List<ProductItem>,
    override val total: Int,
) : CartScreenState()

