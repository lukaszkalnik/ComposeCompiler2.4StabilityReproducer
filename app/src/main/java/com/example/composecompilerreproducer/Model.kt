package com.example.composecompilerreproducer

// Leaf value type — lives in its OWN file and is `internal`. Together with the fact that
// `ProductItem` (its referrer) is also `internal` and in a different file, this is what makes
// Compose compiler 2.4 demote `ProductItem` to `Runtime(AdditionalCost)` (on 2.3.21 it stays
// `Stable`). See app/build/compose_compiler/app-classes.txt after a build.
internal data class AdditionalCost(
    val quantity: Int,
    val singleValue: Int,
)
