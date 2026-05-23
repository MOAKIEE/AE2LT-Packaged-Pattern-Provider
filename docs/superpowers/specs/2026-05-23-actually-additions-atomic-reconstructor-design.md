# Actually Additions Atomic Reconstructor Adapter Design

Date: 2026-05-23

## Goal

Add support for Actually Additions' Atomic Reconstructor default laser conversion recipes while creating a reusable dropped-item dispatch helper for future machines that process item entities in-world.

This adapter supports `actuallyadditions:laser` item conversion recipes only. It intentionally excludes special lens behavior such as mining, color, disenchanting, detonation, and damage lenses.

## Source Behavior

Actually Additions 1.21 implements the Atomic Reconstructor as `de.ellpeck.actuallyadditions.mod.tile.TileEntityAtomicReconstructor`.

Direction is taken from the block state's `BlockStateProperties.FACING`. Placement sets this to `context.getNearestLookingDirection().getOpposite()`, and the tile's `getOrientation()` returns the same property through `WorldUtil.getDirectionByPistonRotation(state)`.

When invoked, `MethodHandler.invokeReconstructor`:

1. Requires at least `TileEntityAtomicReconstructor.ENERGY_USE` energy, currently `1000`.
2. Gets `sideToManipulate = tile.getOrientation()`.
3. Gets `currentLens = tile.getLens()`.
4. Requires `currentLens.canInvoke(tile, sideToManipulate, ENERGY_USE)`.
5. Extracts the `1000` base energy.
6. Walks from `tile.getPosition().relative(sideToManipulate, 1)` up to `currentLens.getDistance()`.
7. Calls `currentLens.invoke(blockState, hitBlock, tile)` for each position until the lens returns true or the maximum distance is reached.

The default conversion lens is `LensConversion`, distance 10. It delegates to `MethodHandler.invokeConversionLens`.

For dropped items, `invokeConversionLens` builds an AABB from the reconstructor block position to `hitBlock + 1`, inflates it by `0.02`, then expands it one more block toward the facing direction. It finds `ItemEntity` instances inside that AABB, ignores entities with persistent boolean `aa_cnv`, finds the first matching `LaserRecipe`, converts as many items as energy allows, discards the original entity, optionally respawns leftover input, spawns output at the original entity coordinates, marks the output entity with `aa_cnv = true`, extracts per-item recipe energy, and stops after one converted entity.

## Architecture

### Reusable Dropped-Item Helper

Add `DroppedItemDispatch` under `logic.multiblock`.

Responsibilities:

- Create item entities at a caller-provided drop position with zero motion and pickup delay.
- Optionally mark helper-created input entities so adapters can distinguish their own entities from player items.
- Scan a bounded AABB for `ItemEntity` outputs that match an `AllowedOutputFilter` and an optional entity predicate.
- Extract by shrinking or discarding entities, returning `GenericStack` instances for the removed outputs.
- Avoid absorbing helper-created input entities unless they have become allowed outputs.

The helper does not know recipe semantics. It only handles entity placement and filtered collection.

### Atomic Reconstructor Adapter

Add `ActuallyAdditionsAtomicReconstructorAdapter` in `logic.multiblock.aa`.

`recognizesMain` returns true only when:

- Actually Additions is loaded.
- The block id is `actuallyadditions:atomic_reconstructor`.
- The block entity is an instance of `TileEntityAtomicReconstructor`.

`plan` performs these checks:

- Main block is still loaded and recognized.
- The current lens is the default conversion lens (`LensConversion`).
- Pattern has one item input and one item output.
- A matching `actuallyadditions:laser` recipe exists.
- Output respects `STRICT` or `ID_ONLY` output mode.
- The reconstructor has enough energy for `1000 + recipeEnergy * inputCount`.
- Every block position from one to ten blocks along the facing direction is loaded.
- The planned drop AABB is small and deterministic.

Dispatch uses a single custom target. In `SIMULATE`, it repeats the environment checks. In `MODULATE`, it spawns the input stack as an `ItemEntity` at the center of the first block in front of the reconstructor.

`onCommit` reflectively calls Actually Additions' own reconstructor invocation method directly: `ActuallyAdditionsAPI.methodHandler.invokeReconstructor(tile)`. The adapter does not simulate redstone pulses or wait for the 100 tick automatic timer. This keeps AA responsible for the actual laser behavior, energy extraction, `aa_cnv` output marker, and partial conversion rules.

`extractOutputs` scans the AA-derived item AABB and removes only allowed output item entities that carry AA's `aa_cnv` conversion marker. This method is also what recovers outputs after the laser runs, because AA spawns conversion results as item entities rather than storing them in a block inventory.

## Drop And Collection Geometry

Drop position:

- `dropBlock = mainPos.relative(facing)`
- Entity coordinates: `dropBlock.x + 0.5`, `dropBlock.y + 0.5`, `dropBlock.z + 0.5`

This guarantees the dropped input lies on the laser path for all six facings.

Collection AABB:

- Determine the same hit block that the default lens would reach when the path is clear: `mainPos.relative(facing, 10)`.
- Build the AA item-conversion AABB:
  - `new AABB(mainPos.x, mainPos.y, mainPos.z, hitBlock.x + 1, hitBlock.y + 1, hitBlock.z + 1)`
  - `.inflate(0.02, 0.02, 0.02)`
  - `.expandTowards(facing.normal.x, facing.normal.y, facing.normal.z)`

The adapter uses this AABB for output extraction. It may also use a small drop-local AABB for preflight checks, but output collection is based on AA's own conversion search region.

Obstacle handling:

- AA stops the default conversion lens when `LensConversion.invoke` sees a non-air hit state.
- For item-only automation, the adapter should prefer an unobstructed ten-block air path. If any loaded block in front is non-air, `plan` returns null. This avoids converting nearby blocks and makes the collection region predictable.

## Data Flow

1. AE requests one laser conversion craft.
2. Adapter validates recipe, output, lens, energy, and path.
3. Dispatch simulation confirms the drop can still be made.
4. Dispatch modulation spawns the input item entity at `mainPos.relative(facing)`.
5. `onCommit` invokes the reconstructor.
6. AA converts the dropped entity and spawns the output entity at the same location.
7. Packaged provider auto-return calls `extractOutputs`.
8. The adapter removes `aa_cnv`-marked allowed output entities from the AA laser AABB and returns them to AE.

## Error Handling

- Missing mod, changed reflection surface, wrong lens, insufficient energy, unloaded path, blocked path, non-item inputs, or unmatched recipe all return null from `plan`.
- Race failures during dispatch return zero accepted amount, allowing the existing `DispatchExecutor` to return the input to AE.
- `extractOutputs` never returns an item without actually removing it from the world.
- Reflection catches `ReflectiveOperationException`, `RuntimeException`, and `LinkageError`.

## Tests

Unit tests should cover the reusable helper math and adapter-independent matching logic:

- Directional drop position for all six facings.
- AA-style collection AABB includes the first-front-block center for all six facings.
- AA-style collection AABB reaches the ten-block default lens path.
- Output matching respects `STRICT` and `ID_ONLY` modes.

Compile verification should run with `./gradlew test`.

Manual in-game verification:

- Empty/default lens, unobstructed path, enough energy: one laser recipe dispatches and returns the output.
- Wrong/special lens: provider waits and does not drop input.
- Block in front of reconstructor: provider waits and does not drop input.
- Insufficient energy: provider waits and does not drop input.
- Unrelated dropped item in the collection AABB: not absorbed unless it matches the provider output filter.
