package com.example.composecompilerreproducer

// Leaf types live here, in their OWN file, and are `internal`.
//
// The user-reported regression is specifically about the changed stability inference for
// INTERNAL types ACROSS DIFFERENT FILES in Compose compiler 2.4:
//
//   "Starting from Kotlin 2.4.0, the Compose compiler offers more consistent incremental
//    compilation. Stability of internal types across different files is now inferred during
//    runtime. This allows Compose to update inferred stability values even when class usages
//    are not recompiled."
//
// That is why CostType / AdditionalCost / ProductItem / ScreenState are each `internal` and
// each in a SEPARATE file (Model.kt / ProductItem.kt / ScreenState.kt).

internal enum class CostType {
    Deposit,
    PackagingTax,
}

// Leaf value type - reports `stable` on BOTH 2.3.21 and 2.4.0.
internal data class AdditionalCost(
    val type: CostType,
    val quantity: Int,
    val singleValue: Int,
)



