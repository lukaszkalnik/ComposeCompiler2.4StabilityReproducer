package com.example.composecompilerreproducer

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The state lives in a [MutableStateFlow] and every "add" creates a brand-new list + a brand-new
 * [ScreenState] (NOT in-place mutation), so structural equality genuinely differs between emissions.
 */
internal class CartViewModel : ViewModel() {

    private val _state = MutableStateFlow<CartScreenState>(initialState())
    val state: StateFlow<CartScreenState> = _state.asStateFlow()

    private var counter = 0

    /** PREPENDS a new product at index 0, producing a new list + new ScreenState each time. */
    fun addProduct() {
        val id = "code-${counter++}"
        val newItem = ProductItem(
            scanCode = id,
            additionalCost = AdditionalCost(value = 25),
        )
        _state.update { current ->
            val prev = current as? ScreenState
            val next = ScreenState(items = listOf(newItem) + prev?.items.orEmpty())
            android.util.Log.d("Recompose", "VM emit: ${next.items.size} items")
            next
        }
    }

    private fun initialState() = ScreenState(
        items = listOf(
            ProductItem(scanCode = "code-${counter++}", additionalCost = AdditionalCost(value = 10)),
        ),
    )
}
