package com.example.composecompilerreproducer

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * Mirror the real SDK: the collected state is a SEALED BASE type ([CartScreenState]) with (at least)
 * two subclasses. The UI collects the base type and `when`-dispatches.
 */
internal sealed class CartScreenState

internal data object EmptyCartScreenState : CartScreenState()

/**
 * TOGGLE FOR THE FIX: removing `@Immutable` here fixes the 2.4.0 build, because the holder then
 * compares by structural equality and [Products] recomposes with the new list. (This is the fix that
 * works — annotating the LEAF [ProductItem] with `@Immutable` does NOT fix it.)
 *
 * NOTE: [items] is an [ImmutableList] (the officially recommended type for Compose stability), yet
 * the bug STILL reproduces — so switching to `ImmutableList` is NOT a fix on its own; the
 * `@Immutable` holder is still wrongly skipped.
 */
@Immutable
internal data class ScreenState(
    val items: ImmutableList<ProductItem>,
) : CartScreenState()
