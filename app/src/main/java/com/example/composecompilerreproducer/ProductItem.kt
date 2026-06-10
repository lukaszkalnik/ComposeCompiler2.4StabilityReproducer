package com.example.composecompilerreproducer

// The item type, in its OWN file and `internal`. It references the `internal` leaf
// `AdditionalCost`, which lives in a DIFFERENT file — that combination is what makes Compose
// compiler 2.4 demote this type to `Runtime(AdditionalCost)` (on 2.3.21 it stays `Stable`).
//
// NOTE: annotating this leaf with @Immutable does NOT fix the bug — the holder `ScreenState`
// stays `@Immutable` and is still wrongly skipped. The only fix is to remove `@Immutable` from
// the holder (see ScreenState.kt).
internal data class ProductItem(
    val scanCode: String, // stable key for LazyColumn
    val additionalCost: AdditionalCost?,
)
