package com.example.composecompilerreproducer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Mirrors `CartViewModel.handleCartWithProducts`: the state lives in a [MutableStateFlow]
 * and every "add" creates a brand-new list + a brand-new [ScreenState] (NOT in-place
 * mutation), so structural equality genuinely differs between emissions.
 */
internal class CartViewModel : ViewModel() {

    private val _state = MutableStateFlow<CartScreenState>(initialState())
    val state: StateFlow<CartScreenState> = _state.asStateFlow()

    private var counter = 0

    /**
     * Replicates `CartRepository.addNewProductToCart`: PREPENDS a new product at index 0,
     * producing a new list + new ScreenState each time.
     *
     * Also replicates the real VM's DOUBLE emission: first an `isLoading = true`
     * (Evaluating) state, then ~120ms later the `isLoading = false` (Evaluated) state.
     */
    fun addProduct() {
        val id = "code-${counter++}"
        val newItem = ProductItem(
            scanCode = id,
            name = "Product $id",
            additionalCost = AdditionalCost(
                type = CostType.Deposit,
                quantity = 1,
                singleValue = 25,
            ),
            isLoading = true,
        )

        // Emission #1: loading / "Evaluating"
        _state.update { current ->
            val items = (current as? ScreenState)?.items.orEmpty()
            val total = current.total
            val next = ScreenState(
                items = listOf(newItem) + items,
                total = total + 100,
            )
            android.util.Log.d("Recompose", "VM emit #1: ${next.items.size} items, total=${next.total}")
            next
        }

        // Emission #2: evaluated, ~120ms later
        viewModelScope.launch {
            delay(120)
            _state.update { current ->
                val items = (current as? ScreenState)?.items.orEmpty()
                val next = ScreenState(
                    items = items.map {
                        if (it.scanCode == id) it.copy(isLoading = false) else it
                    },
                    total = current.total,
                )
                android.util.Log.d("Recompose", "VM emit #2: ${next.items.size} items, total=${next.total}")
                next
            }
        }
    }

    private fun initialState(): ScreenState {
        val first = ProductItem(
            scanCode = "code-${counter++}",
            name = "Product code-0",
            additionalCost = AdditionalCost(CostType.PackagingTax, quantity = 1, singleValue = 10),
            isLoading = false,
        )
        return ScreenState(items = listOf(first), total = 100)
    }
}
