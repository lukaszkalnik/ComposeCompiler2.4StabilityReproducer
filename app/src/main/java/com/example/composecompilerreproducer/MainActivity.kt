package com.example.composecompilerreproducer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val TAG = "Recompose"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val viewModel: CartViewModel = viewModel()
                Screen(
                    viewModel = viewModel,
                    onAdd = viewModel::addProduct,
                )
            }
        }
    }
}

@Composable
internal fun Screen(
    viewModel: CartViewModel,
    onAdd: () -> Unit,
) {
    // Mirror the real pipeline: StateFlow<CartScreenState (sealed base)> + a SEPARATE mission
    // StateFlow, both via collectAsStateWithLifecycle.
    val state by viewModel.state.collectAsStateWithLifecycle()
    val missionState by viewModel.missionState.collectAsStateWithLifecycle()
    SideEffect { Log.d(TAG, "Screen recomposed: state=${(state as? ScreenState)?.items?.size}") }

    Scaffold { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Add product (prepends a new item)")
            }
            // Mirror the real SDK CartScreen: `when`-dispatch over the sealed base type.
            CartScreen(state = state, missionState = missionState)
        }
    }
}

@Composable
internal fun CartScreen(state: CartScreenState, missionState: MissionTooltipState) {
    SideEffect { Log.d(TAG, "CartScreen recomposed: state=${(state as? ScreenState)?.items?.size}") }
    when (state) {
        is EmptyCartScreenState -> Text("Empty")
        is ScreenState -> Products(state = state, missionState = missionState)
    }
}

/**
 * The key composable. Mirrors the SDK's `CartProducts`: it receives the `@Immutable ScreenState`
 * holder AND a separate `Uncertain` `MissionTooltipState`, and the LazyColumn `content` lambda
 * CAPTURES the `Uncertain` value and reads it inside the item conditional (just like the SDK's
 * MissionTooltip branch). This is the strong-skipping-relevant shape the flat reproducer lacked.
 *
 * On 2.4.0, because `ProductItem` is inferred `runtime(...)`, strong skipping can decide
 * this composable does not need to re-run for the new `ScreenState`, so the LazyColumn
 * `content` lambda is never invoked with the new list -> the prepended item is missing.
 */
@Composable
internal fun Products(state: ScreenState, missionState: MissionTooltipState) {
    SideEffect { Log.d(TAG, "Products recomposed: ${state.items.size} items, total=${state.total}") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Mirror the real SDK: a leading non-indexed item header before the indexed items, and
        // each item uses Modifier.animateItem() plus a trailing divider.
        item {
            HorizontalDivider(modifier = Modifier.animateItem())
        }

        itemsIndexed(
            items = state.items,
            key = { _, item -> item.scanCode },
        ) { _, item ->
            // Mirror the SDK: the content lambda captures the `Uncertain` missionState and reads
            // it in a conditional, exactly like the MissionTooltip branch in CartProducts.
            // NOTE: this capture is NOT required to trigger the bug — verified by bisection that
            // the intermediate CartScreen dispatch alone reproduces it — but it mirrors the SDK.
            if (item.scanCode == missionState.lastModifiedScanCode && missionState.missionDetails != null) {
                Text("mission: ${missionState.missionDetails.reward}")
            } else {
                ProductRow(item = item, modifier = Modifier.animateItem())
            }
            HorizontalDivider(modifier = Modifier.animateItem())
        }
    }
}

@Composable
internal fun ProductRow(item: ProductItem, modifier: Modifier = Modifier) {
    SideEffect { Log.d(TAG, "ProductRow recomposed: ${item.scanCode}") }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "scanCode: ${item.scanCode}")
            item.additionalCost?.let { cost ->
                Text(text = "${cost.type} x${cost.quantity} @ ${cost.singleValue}")
            }
            if (item.isLoading) {
                Text(text = "Loading…")
            }
        }
    }
}





