# NOTES TO SELF — build a runtime reproducer for the LazyColumn "missing 2nd item" bug

## ✅ RESOLVED (2026-06-10) — the runtime bug now reproduces on device (Pixel 7)

The original flat reproducer (`Screen` collects the flow and calls `Products(state)` directly)
produced the correct **compiler-report** diff (`ProductItem` → `Runtime(AdditionalCost)`), but
rendered fine at runtime. The missing ingredient was the **UI pipeline shape**, not the models.

**Essential trigger (verified by bisection on device):** an intermediate `restartable` composable
(`CartScreen`) that

1. is fed a **sealed base type** `CartScreenState` **with ≥2 subclasses** (e.g.
   `EmptyCartScreenState` + `ScreenState`) — a single-subclass sealed type or a plain class does
   **not** reproduce,
2. `when`-dispatches and **passes the `@Immutable` holder (`ScreenState`) down as a parameter**
   to `Products`,

while `ScreenState` holds a `List<ProductItem>` where `ProductItem` is `internal` and references
the `internal` leaf `AdditionalCost` in a **different file** (→ 2.4 demotes `ProductItem` to
`Runtime(AdditionalCost)`).

That mirrors the real SDK chain `CartScreen(CartScreenState) → CartWithItemsScreen → CartProducts`,
which the flat reproducer had collapsed into one composable.

**On-device result (same code, only the Kotlin/compiler version changes):**

| Config (default BOM 2026.05.01)                          | Render                           |
|----------------------------------------------------------|----------------------------------|
| Kotlin **2.4.0**, holder `ScreenState` `@Immutable`      | ❌ stuck at 1 item (`code-0`)     |
| Kotlin **2.3.21**, identical code                        | ✅ all items render               |
| Kotlin 2.4.0, **remove `@Immutable` from holder**        | ✅ all items render (the fix)     |
| Kotlin 2.4.0, add `@Immutable` to **leaf** `ProductItem` | ❌ still stuck — does **not** fix |

Smoking-gun logs on 2.4.0 (one "Add"):

```
VM emit: 2 items
CartScreen recomposed: state=2          ← parent sees the new 2-item state
Products recomposed: 1 items            ← child re-runs with the STALE 1-item state
```

`CartScreen` holds the new state but the call `Products(state=new)` is **wrongly skipped** (the
`@Immutable` holder is treated as unchanged by 2.4's runtime stability inference of its
`Runtime(...)` member), so `Products` keeps its stale captured `state` → the prepended item never
appears.

Notes:

- The `Uncertain` `MissionTooltipState` param (Uuid-based `MissionDetails`) was **removed**: it is
  **not** part of the trigger (verified on device — the bug still reproduces without it).
- Other SDK features tested and ruled OUT as the trigger: newer Compose BOM (2026.05.01),
  `Modifier.animateItem()`, the leading `item {}` header. Duplicate LazyColumn keys were also
  ruled out — they **crash** (`Key "…" was already used`) rather than silently drop an item.
- The relevant fix for the SDK is **removing `@Immutable` from the holder** (matches the
  user-confirmed fix), NOT annotating the leaf.

---

# (original investigation notes below)

# NOTES TO SELF — build a runtime reproducer for the LazyColumn "missing 2nd item" bug

Status: TODO. Goal is a tiny standalone Android Compose project that **actually reproduces the missing
item at runtime** (not just the compiler-report stable→runtime diff, which we already have).

We have proven the *what* (Compose compiler 2.3.21 → 2.4.0 demotes `ProductCartItemState` /
`MissionDetails` from `stable` to `runtime`/`Uncertain`). We have NOT yet mechanically nailed the
*exact runtime skip path* that makes the second item vanish. The reproducer is to lock that down.

## 0. Success criteria

- [ ] On Kotlin/Compose-compiler **2.4.0**: adding a 2nd item to the list → item does NOT render.
- [ ] Same code on **2.3.21**: item DOES render.
- [ ] Toggling the fix (annotate leaf `@Immutable`, OR remove `@Immutable` from holder) → renders on 2.4.0.
- [ ] Captured: compiler stability report + recomposition counts + screen recording for each case.

## 1. Project skeleton

- [ ] New empty Compose app (single module `app`), Material3, `androidx.activity:activity-compose`.
- [ ] Two switchable toolchains via a single `gradle.properties` / version-catalog edit:
  `kotlin = "2.4.0"` ↔ `"2.3.21"` (Compose compiler ships with Kotlin, so this is the only knob).
- [ ] Keep Compose **runtime/BOM identical** across both builds (prove it's the compiler, not runtime).
- [ ] Enable reports in `app/build.gradle.kts`:
  ```kotlin
  composeCompiler {
      reportsDestination = layout.buildDirectory.dir("compose_compiler")
      metricsDestination = layout.buildDirectory.dir("compose_compiler")
  }
  ```
- [ ] Mirror the real flags from the SDK: `-Xannotation-default-target=param-property`,
  `explicitApi`, jvmTarget 11. (The annotation-target default change is a prime suspect — keep it
  as a toggle, see §4.)

## 2. Reproduce the exact shape from the SDK (don't simplify away the trigger)

The trigger is a leaf type that 2.4.0 demotes. Mirror `ProductCartItemState`'s shape:

- [ ] `enum class CostType { Deposit, PackagingTax }`
- [ ] `data class AdditionalCost(val type: CostType, val quantity: Int, val singleValue: Int)` —
  reports `stable` in both versions.
- [ ] Leaf item references it:
  ```kotlin
  data class ProductItem(
      val scanCode: String,         // key
      val name: String,
      val additionalCost: AdditionalCost?,   // <-- the field that makes 2.4.0 say Runtime(AdditionalCost)
  )
  ```
- [ ] Holder annotated like the real `CartWithItemsScreenState`:
  ```kotlin
  @Immutable
  data class ScreenState(val items: List<ProductItem>) : CartScreenState()
  ```
- [ ] Confirm in the report: 2.4.0 → `ProductItem` = `runtime`, `ScreenState` = `stable` (via @Immutable).

## 3. Reproduce the real UI pipeline (nesting matters — don't flatten it)

Mirror Cart → Products → LazyColumn, fed by a StateFlow:

- [ ] `MutableStateFlow<ScreenState>` in a ViewModel; UI via `collectAsStateWithLifecycle()`.
- [ ] `@Composable Screen(state)` → `@Composable Products(state)` → `LazyColumn { itemsIndexed(
      state.items, key = { _, it -> it.scanCode }) { ... ProductRow(it) } }`.
- [ ] `ProductRow(item: ProductItem)` reads several fields incl. `additionalCost`.
- [ ] Use `Modifier.animateItem()` like the real screen (keep as a toggle — could interact).
- [ ] "Add" button **prepends** a new item at index 0 (matches `addNewProductToCart` which inserts at 0),
  producing a NEW list + NEW ScreenState each time.

## 4. Experiment matrix (flip ONE at a time; record renders? + report verdict)

- [ ] compiler: **2.3.21 vs 2.4.0**  ← primary
- [ ] holder `@Immutable`: on vs off ← user-confirmed fix is "off"
- [ ] leaf `ProductItem` `@Immutable`: on vs off ← proposed fix
- [ ] item position: **prepend(0)** vs append(end)
- [ ] `key`: present (`scanCode`) vs absent
- [ ] `animateItem()`: on vs off
- [ ] `collectAsStateWithLifecycle` vs `collectAsState` vs direct `mutableStateOf`
- [ ] flag `-Xannotation-default-target=param-property`: present vs absent (and try
  `languageVersion=2.3` under the 2.4 compiler to see if it's language-version gated)

Goal: smallest combination that flips render? between 2.3.21 and 2.4.0.

## 5. What to capture per run

- [ ] `build/compose_compiler/*-classes.txt` + `*-composables.txt` (verdict for ScreenState/ProductItem/Products).
- [ ] Recomposition counts: composition tracing OR a manual `SideEffect { Log.d("recompose", "...") }`
  / `Ref` counter per composable (Screen / Products / each Row / the LazyColumn content).
- [ ] Layout Inspector "recomposition counts" highlight while pressing Add.
- [ ] Screen recording / screenshot proving the item is missing vs present.

## 6. Hypotheses to confirm/kill (we never fully proved the runtime path)

- [ ] H1: `Products` (or `Screen`) is being **skipped** so the LazyColumn `content` lambda isn't
  re-run with the new list. Check recompose counts of `Products` on Add.
- [ ] H2: The LazyColumn item **content lambda is memoized** (strong skipping) such that the new list
  isn't observed. Check whether removing the holder `@Immutable` (→ Products recomposes by ===)
  is what actually re-runs the content.
- [ ] H3: `key` collision (duplicate/blank `scanCode`) independently drops the 2nd item — RULE OUT by
  using guaranteed-unique keys, since real fractional-qty products can reuse productId.
- [ ] Expectation: removing holder `@Immutable` OR annotating leaf `@Immutable` both fix → confirms the
  stability mismatch is the cause, consistent with the report diff.

## 7. Stretch

- [ ] Bisect exact compiler build between 2.3.21 and 2.4.0 (RC/Beta if available) to pin the change.
- [ ] Minimize to the SMALLEST repro (drop nesting/animateItem/double-emit if not needed) for the
  Google Issue Tracker attachment (see docs/google-issuetracker-compose-stability-regression.md).
- [ ] If runtime repro proves elusive but the report diff is real → file as "stability inference
  regression + needs release-note communication" regardless (the report diff alone is enough).

## 8. Links / cross-refs

- Issue draft: `docs/google-issuetracker-compose-stability-regression.md`
- Real code: `CartViewModel.handleCartWithProducts`, `CartWithItemsScreen`/`CartProducts`,
  `ProductCartItemState`, `MissionDetails`. Real "add" path: `CartRepository.addNewProductToCart`
  (prepends at index 0, new list each time → so NOT in-place mutation; equality genuinely differs).
- Also consider cross-posting to Kotlin YouTrack (KT) since the compiler ships with Kotlin.

