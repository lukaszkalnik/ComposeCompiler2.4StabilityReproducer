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
      val isLoading: Boolean,
  )
  ```
- [ ] Holder annotated like the real `CartWithItemsScreenState`:
  ```kotlin
  @Immutable
  data class ScreenState(val items: List<ProductItem>, val total: Int)
  ```
- [ ] Confirm in the report: 2.4.0 → `ProductItem` = `runtime`, `ScreenState` = `stable` (via @Immutable).
  If `ProductItem` doesn't go `runtime`, add the `kotlin.uuid.Uuid`/sealed variant too (see §2b).

### 2b. Also try the sealed/`Uuid` variant (the `Uncertain` case)

- [ ] `sealed class MissionDetails { data class A(val id: kotlin.uuid.Uuid, val n: Int) : MissionDetails() }`
- [ ] Holder `data class TooltipState(val details: MissionDetails?, val show: Boolean)`.
- [ ] Confirm 2.4.0 → `MissionDetails` = `Uncertain`. Suspect: `kotlin.uuid.Uuid` value class. Keep a
  variant WITHOUT `Uuid` (use `String` id) to isolate whether Uuid is the trigger.

## 3. Reproduce the real UI pipeline (nesting matters — don't flatten it)

Mirror Cart → Products → LazyColumn, fed by a StateFlow:

- [ ] `MutableStateFlow<ScreenState>` in a ViewModel; UI via `collectAsStateWithLifecycle()`.
- [ ] `@Composable Screen(state)` → `@Composable Products(state)` → `LazyColumn { itemsIndexed(
      state.items, key = { _, it -> it.scanCode }) { ... ProductRow(it) } }`.
- [ ] `ProductRow(item: ProductItem)` reads several fields incl. `additionalCost`.
- [ ] Use `Modifier.animateItem()` like the real screen (keep as a toggle — could interact).
- [ ] "Add" button **prepends** a new item at index 0 (matches `addNewProductToCart` which inserts at 0),
  producing a NEW list + NEW ScreenState each time.
- [ ] Replicate the **double emission** the real VM does: emit `isLoading=true` (Evaluating) then
  `isLoading=false` (Evaluated) ~100ms apart. (Real `handleCartWithProducts` runs per cart emission.)

## 4. Experiment matrix (flip ONE at a time; record renders? + report verdict)

- [ ] compiler: **2.3.21 vs 2.4.0**  ← primary
- [ ] holder `@Immutable`: on vs off ← user-confirmed fix is "off"
- [ ] leaf `ProductItem` `@Immutable`: on vs off ← proposed fix
- [ ] item position: **prepend(0)** vs append(end)
- [ ] `key`: present (`scanCode`) vs absent
- [ ] `animateItem()`: on vs off
- [ ] emissions: single vs double(loading→evaluated)
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
- [ ] H4: It's the **double emission** + skipping interaction, not single add.
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

