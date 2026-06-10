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
    // Mirror the real pipeline: StateFlow -> collectAsStateWithLifecycle.
    val state by viewModel.state.collectAsStateWithLifecycle()
    SideEffect { Log.d(TAG, "Screen recomposed: ${state.items.size} items") }

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
            Products(state = state)
        }
    }
}

/**
 * The key composable. Its only parameter is the `@Immutable ScreenState` holder.
 *
 * On 2.4.0, because `ProductItem` is inferred `runtime(...)`, strong skipping can decide
 * this composable does not need to re-run for the new `ScreenState`, so the LazyColumn
 * `content` lambda is never invoked with the new list -> the prepended item is missing.
 */
@Composable
internal fun Products(state: ScreenState) {
    SideEffect { Log.d(TAG, "Products recomposed: ${state.items.size} items, total=${state.total}") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = state.items,
            key = { _, item -> item.scanCode },
        ) { _, item ->
            ProductRow(item = item)
        }
    }
}

@Composable
internal fun ProductRow(item: ProductItem) {
    SideEffect { Log.d(TAG, "ProductRow recomposed: ${item.scanCode}") }

    Card(modifier = Modifier.fillMaxWidth()) {
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





