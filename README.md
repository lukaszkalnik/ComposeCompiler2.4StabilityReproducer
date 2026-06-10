# Compose Compiler 2.4 — `internal` stability-inference regression reproducer

A minimal Android Compose project that reproduces a behaviour change introduced in the
**Compose compiler that ships with Kotlin 2.4.0**.

> Starting from Kotlin 2.4.0, the Compose compiler offers more consistent incremental
> compilation. **Stability of internal types across different files is now inferred during
> runtime.** This allows Compose to update inferred stability values even when class usages
> are not recompiled.

In the real app the effect was: a UI-state class annotated `@Immutable` that references a
`List<ProductItem>`, where `ProductItem` contains another (stable) type `AdditionalCost?`,
**stopped propagating list updates to the UI**. When a new `ProductItem` was added to the
list, it did not appear on screen.

---

## TL;DR — what this project proves

The single knob is the Kotlin version in [`gradle/libs.versions.toml`](gradle/libs.versions.toml)
(the Compose compiler ships *with* Kotlin, so this switches the compiler too). Everything
else — Compose BOM/runtime, AGP, code — is held identical.

The Compose compiler stability report (`app/build/compose_compiler/app-classes.txt`) flips for
the leaf item type:

| Type             | Kotlin **2.3.21**       | Kotlin **2.4.0**                |
|------------------|-------------------------|---------------------------------|
| `AdditionalCost` | `stable`                | `stable`                        |
| `ProductItem`    | **`stable`**            | **`Runtime(AdditionalCost)`** ⬅ |
| `ScreenState`    | `stable` (`@Immutable`) | `stable` (`@Immutable`)         |

Both verdicts above were produced by actually building this project on each version.

Two conditions are required to trigger the demotion, and they mirror the real SDK exactly:

1. The types are **`internal`**, and
2. The leaf type (`AdditionalCost`) lives in a **different file** from the type that
   references it (`ProductItem`).

That is why the model types are each `internal` and each in their own file
(`Model.kt`, `ProductItem.kt`, `ScreenState.kt`).

---

## Project shape (mirrors the real Cart screen)

```
StateFlow<ScreenState>            (CartViewModel)
        │  collectAsStateWithLifecycle()
        ▼
Screen(state)                     reads state, hosts the "Add" button
        ▼
Products(state: ScreenState)      @Immutable param → skippable; hosts the LazyColumn
        ▼
LazyColumn { itemsIndexed(items, key = { _, it -> it.scanCode }) { ProductRow(it) } }
        ▼
ProductRow(item: ProductItem)
```

Model types (each `internal`, each in its own file):

```kotlin
// Model.kt
internal data class AdditionalCost(val quantity: Int, val singleValue: Int)

// ProductItem.kt
internal data class ProductItem(
    val scanCode: String,                 // LazyColumn key
    val name: String,
    val additionalCost: AdditionalCost?,  // ← the field 2.4.0 resolves at runtime
)

// ScreenState.kt
@Immutable
internal data class ScreenState(val items: List<ProductItem>, val total: Int)
```

`CartViewModel`:

- the "Add" action **prepends** a brand-new `ProductItem` at index 0, producing a **new list +
  new `ScreenState`** each time (no in-place mutation — structural equality genuinely differs).

---

## How to run

```bash
# default = Kotlin 2.4.0 (the broken case)
./gradlew :app:assembleDebug
./gradlew :app:installDebug      # or run from Android Studio
```

Press **"Add product"** repeatedly and watch the list. Recomposition logging is wired via
`SideEffect { Log.d("Recompose", ...) }` in `Screen`, `Products` and each `ProductRow`:

```bash
adb logcat -s Recompose
```

---

## Switching compiler versions (the one knob)

Edit [`gradle/libs.versions.toml`](gradle/libs.versions.toml):

```toml
kotlin = "2.4.0"     # BROKEN
# kotlin = "2.3.21"  # WORKS
```

…flip the comment to use `2.3.21`, then rebuild. The Compose BOM/runtime is pinned and shared
across both, so any difference is attributable to the compiler.

> Note: this module uses **AGP 9's built-in Kotlin** (no separate `org.jetbrains.kotlin.android`
> plugin — AGP 9.0+ rejects it). The `kotlin` version in the catalog still drives the Compose
> compiler plugin, and the stability verdict was verified to flip between the two versions.

---

## Inspecting the compiler stability reports

Reports/metrics are enabled in [`app/build.gradle.kts`](app/build.gradle.kts):

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

After a build:

```bash
cat app/build/compose_compiler/app-classes.txt       # per-class stability verdict
cat app/build/compose_compiler/app-composables.txt    # skippable/restartable per composable
```

Look for `ProductItem`: `Runtime(AdditionalCost)` on 2.4.0 vs `Stable` on 2.3.21.

---

## The two fixes (each independently makes 2.4.0 behave like 2.3.21)

Both are wired as one-line toggles in the source:

1. **Annotate the leaf** `ProductItem` with `@Immutable` — uncomment the line above the class
   in [`ProductItem.kt`](app/src/main/java/com/example/composecompilerreproducer/ProductItem.kt).
2. **Remove `@Immutable` from the holder** `ScreenState` in
   [`ScreenState.kt`](app/src/main/java/com/example/composecompilerreproducer/ScreenState.kt)
   so it is compared by structural equality and `Products` recomposes with the new list.

---

## Experiment matrix (flip ONE at a time)

| Variable                                     | Values                                         |
|----------------------------------------------|------------------------------------------------|
| compiler (`kotlin` in the catalog)           | **2.3.21** vs **2.4.0**  ← primary             |
| holder `ScreenState` `@Immutable`            | on vs off                                      |
| leaf `ProductItem` `@Immutable`              | on vs off                                      |
| types `internal`                             | on vs off (off → `ProductItem` stays `stable`) |
| same file vs different files                 | different (current) vs merged into one file    |
| add position                                 | prepend(0) (current) vs append(end)            |
| LazyColumn `key`                             | present (`scanCode`) vs absent                 |
| `-Xannotation-default-target=param-property` | present (current) vs absent                    |

---

## Files of interest

- `app/src/main/java/.../Model.kt` — `AdditionalCost` (leaf, stable on both)
- `app/src/main/java/.../ProductItem.kt` — `ProductItem` (demoted to `Runtime` on 2.4.0)
- `app/src/main/java/.../ScreenState.kt` — `@Immutable` holder + sealed `CartScreenState` base
- `app/src/main/java/.../CartViewModel.kt` — prepend (new list + new `ScreenState`)
- `app/src/main/java/.../MainActivity.kt` — `Screen` → `CartScreen` → `Products` → `LazyColumn` → `ProductRow`
- `app/build.gradle.kts` — Compose setup + compiler reports + `-Xannotation-default-target`
- `gradle/libs.versions.toml` — the version toggle

See [`NOTES-lazycolumn-repro.md`](NOTES-lazycolumn-repro.md) for the original investigation notes.

