package com.example.composecompilerreproducer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            CartScreen(state = state)
        }
    }
}

@Composable
internal fun CartScreen(state: CartScreenState) {
    SideEffect { Log.d(TAG, "CartScreen recomposed: state=${(state as? ScreenState)?.items?.size}") }
    when (state) {
        is EmptyCartScreenState -> Text("Empty")
        is ScreenState -> Products(state = state)
    }
}

@Composable
internal fun Products(state: ScreenState) {
    SideEffect { Log.d(TAG, "Products recomposed: ${state.items.size} items") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        items(items = state.items, key = { it.scanCode }) { item ->
            ProductRow(item = item)
        }
    }
}

@Composable
internal fun ProductRow(item: ProductItem) {
    SideEffect { Log.d(TAG, "ProductRow recomposed: ${item.scanCode}") }
    Text(text = item.scanCode, modifier = Modifier.padding(16.dp))
}
