package com.example.composecompilerreproducer

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Mirrors the real SDK's `MissionDetails`: an `internal` sealed class in its OWN file whose
// leaf references `kotlin.uuid.Uuid`. On Compose compiler 2.4 the report demotes this to
// `Uncertain` (the second regression case described in the NOTES).
//
// The point of this file: the LazyColumn `content` lambda in the SDK CAPTURES a value of an
// `Uncertain` type (`missionTooltipState` / `missionDetails`) and reads it inside the item
// conditional. The original reproducer's `Products` only ever saw the stable `ScreenState`, so
// the strong-skipping memoization of the LazyColumn content lambda never had an `Uncertain`
// capture. This file lets us reproduce that capture.
@OptIn(ExperimentalUuidApi::class)
internal sealed class MissionDetails {

    internal abstract val id: Uuid
    internal abstract val reward: String

    internal data class ArticleCountMissionDetails(
        override val id: Uuid,
        override val reward: String,
        val progress: Int,
        val goal: Int,
    ) : MissionDetails()
}

// Mirrors the real `MissionTooltipState` holder that is collected from a SEPARATE StateFlow and
// threaded into the cart screen alongside the cart state.
internal data class MissionTooltipState(
    val lastModifiedScanCode: String,
    val show: Boolean,
    val missionDetails: MissionDetails?,
)

