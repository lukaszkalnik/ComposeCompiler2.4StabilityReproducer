package com.example.composecompilerreproducer

// The item type, in its OWN file and `internal`.
//
// Note the nullable AdditionalCost reference - this is the field whose stability the
// 2.4.0 compiler now resolves at runtime (instead of baking `stable` in at compile time),
// which is what lets the holder's strong-skipping memoization swallow the new list.
//
// TOGGLE FOR THE FIX: annotate this class with @Immutable (uncomment below) and the
// 2.4.0 build renders correctly again.
// @Immutable
internal data class ProductItem(
    val scanCode: String, // stable key for LazyColumn
    val name: String,
    val additionalCost: AdditionalCost?,
    val isLoading: Boolean,
)

