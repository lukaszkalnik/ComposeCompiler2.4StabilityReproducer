# Compose Compiler 2.4 — `@Immutable` over-skipping regression

Minimal Android Compose project that reproduces a **runtime** behaviour change in the Compose
compiler shipped with **Kotlin 2.4.0**: items added to a `LazyColumn` stop appearing on screen.

> Kotlin 2.4.0 release note: *"Stability of internal types across different files is now inferred
> during runtime."* This reproducer shows that change can make a stable (`@Immutable`) UI-state
> holder be **wrongly skipped**, so list updates never reach the UI.

## Symptom

Press **"Add product"** repeatedly:

- **Kotlin 2.4.0 (default):** only the first item ever renders. The list is stuck. (broken)
- **Kotlin 2.3.21 (same code):** every added item renders. (works)

Reproduces in both **debug** and **R8-minified release** builds.

`adb logcat -s Recompose` shows the parent gets the new state but the child re-runs with the old one:

```
VM emit: 2 items
CartScreen recomposed: state=2     <- parent sees the new 2-item state
Products recomposed: 1 items       <- child is wrongly skipped, keeps the STALE 1-item state
```

## Run it

```bash
./gradlew :app:installDebug      # default = Kotlin 2.4.0 (broken)
./gradlew :app:installRelease    # same bug with R8 minification enabled
```

Switch the compiler via the **one knob** in [`gradle/libs.versions.toml`](gradle/libs.versions.toml)
(the Compose compiler ships with Kotlin, so this is the only thing that changes — BOM/AGP/code are
held constant), then rebuild:

```toml
kotlin = "2.4.0"     # BROKEN
# kotlin = "2.3.21"  # WORKS
```

## The fix

Remove `@Immutable` from the holder `ScreenState`
([`ScreenState.kt`](app/src/main/java/com/example/composecompilerreproducer/ScreenState.kt)). It is
then inferred unstable (it holds a `List`), so `Products` is no longer skippable and re-runs with
the new list.

Annotating the leaf `ProductItem` with `@Immutable` does **not** fix it — the holder stays
`@Immutable` and is still wrongly skipped.

## What is required to trigger it

All four are necessary (each was verified on-device by removing it):

1. **`ProductItem` is `internal` and references the `internal` leaf `AdditionalCost` in a different
   file.** This is what makes the 2.4 compiler report demote `ProductItem` from `Stable` to
   `Runtime(AdditionalCost)`.
2. **The holder `ScreenState` is `@Immutable`** and contains `List<ProductItem>`.
3. **The state is exposed as a sealed base type `CartScreenState` with at least 2 subclasses**
   (`EmptyCartScreenState` + `ScreenState`). A single-subclass sealed type, or a plain class, does
   not reproduce.
4. **An intermediate composable `CartScreen(state: CartScreenState)` `when`-dispatches and passes
   the `@Immutable` holder down** to a child `Products(state: ScreenState)`. The wrong skip happens
   at that `CartScreen -> Products` call. (Collecting the flow and calling `Products` directly does
   not reproduce.)

```
StateFlow<CartScreenState> -> Screen -> CartScreen(when-dispatch) -> Products(ScreenState) -> LazyColumn
```

```kotlin
// Model.kt        (internal leaf, own file)     -> Stable on both
internal data class AdditionalCost(val value: Int)

// ProductItem.kt  (internal, own file)          -> Stable on 2.3.21, Runtime(AdditionalCost) on 2.4.0
internal data class ProductItem(val scanCode: String, val additionalCost: AdditionalCost?)

// ScreenState.kt  (sealed base + @Immutable holder)
internal sealed class CartScreenState
internal data object EmptyCartScreenState : CartScreenState()
@Immutable internal data class ScreenState(val items: List<ProductItem>) : CartScreenState()
```

## Compiler stability report

Reports are enabled in [`app/build.gradle.kts`](app/build.gradle.kts). After a build:

```bash
cat app/build/compose_compiler/app-classes.txt   # look for ProductItem
```

| Type             | Kotlin 2.3.21           | Kotlin 2.4.0                  |
|------------------|-------------------------|-------------------------------|
| `AdditionalCost` | `stable`                | `stable`                      |
| `ProductItem`    | **`stable`**            | **`Runtime(AdditionalCost)`** |
| `ScreenState`    | `stable` (`@Immutable`) | `stable` (`@Immutable`)       |

## Notes

- Uses AGP 9's built-in Kotlin (no separate `org.jetbrains.kotlin.android` plugin; AGP 9+ rejects it).
- The release build type enables R8 (`optimization { enable = true }`, gated by
  `android.r8.gradual.support=true` in `gradle.properties`) and signs with the debug key so it is
  installable.
- Recomposition logging: `SideEffect { Log.d("Recompose", ...) }` in `CartScreen` / `Products`.

