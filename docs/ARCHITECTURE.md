# Heavymatica: architecture and work packages

Heavymatica is a client-side Fabric addon for Litematica + Litematica Array (`schemarray`) that adds
**heavymatics**: named, multi-box area presets (dig / clear / flood / fill / protect) shown in Litematica's placement
list, drawn in the world (wireframes + a vanilla terrain cutaway), and sent into Manhattan (`baritonequeue`) as saved
areas. This file is the source of truth for everyone working on it. If something here is wrong or missing, say so in
your report instead of guessing.

## Fixed decisions

| # | Decision |
|---|---|
| 1 | Manhattan has a public API: `dev.baritonequeue.api.ManhattanAreas` (API_VERSION 1, on `GermanCGArtist/Manhattan` branch `midtown-impl`). |
| 2 | Heavymatica **pushes** areas into it, **with a role**. `PROTECT` areas become Midtown safe areas. |
| 3 | **Explicit "Send to Manhattan"** only. A row shows "out of sync" after any change since the last send. Manhattan refuses (`IN_USE`) while a running script has that area selected. |
| 4 | Shared area names with owner tags. `put` returns `SCRIPT_OWNED` if a script wrote that name: ask the user, then retry with `replaceScriptOwned = true`. The Manhattan listener tells us when a script overwrote one of ours -> mark the row out of sync. |
| 5 | Heavymatica is its **own mod** with its **own data model** (`.heavymatic` JSON, own transforms). It **depends on Litematica Array**, which gets a small extension point (`dev.schemarray.api`) so `[H]` rows live in Litematica's placement list with folders, colors, multi-select and drag & drop. No duplicate-mod detection. |
| 6 | Cutaway works with vanilla and with Sodium 0.8.x: one mesher mixin each, `HeavymaticaMixinPlugin` applies the matching one. Wireframes and hazard highlights always work. |
| 7 | Shapes are split into **exact** boxes. Above 64 boxes: a warning on save, and a confirm before sending to Manhattan. |
| 8 | An array becomes **one** Manhattan area holding the boxes of all copies. |
| 9 | Launch on **MC 26.1.1 only** (Litematica 0.27.14, malilib 0.28.12, Fabric API 0.145.4+26.1.1, Java 25). |
| 10 | The LLM export scans terrain **in the background** with a per-tick block budget: per region the 5 most common blocks, water / lava / air counts, % unscanned (unloaded chunks), and flags (falling blocks at the edges, lava next to the region, overlap with a Manhattan protected area). |

## Hard rules

* **Never** change what `ClientLevel` / `Level#getBlockState` returns. Manhattan's Midtown planner, executor and
  `clear_area` read the world through it; a "visual" hole there makes the bot walk into air that is not there. The
  cutaway only touches chunk-section **meshing** (`SectionCompiler` input).
* **Never** start, stop or kill Minecraft. In-game testing is the user's job; report what needs testing.
* Do not push to GitHub. Commit on the branch you were given, with a clear message ending in
  `Co-Authored-By: Claude <noreply@anthropic.com>`.
* Heavymatica must load and work **without Manhattan installed** (the send button is just hidden).
* Manhattan is reached by **reflection only** (`dev.heavymatica.manhattan.ManhattanBridge`), never compiled against.

## Repos, branches, building

| What | Where | Branch |
|---|---|---|
| Heavymatica | `F:\workflow\Heavymatica` (GitHub `GermanCGArtist/Heavymatica`) | `26.1.1` (one branch per MC version later, `main` = newest) |
| Litematica Array extension point | `F:\workflow\SchematicArray-heavymatica-api` (a worktree of `F:\workflow\SchematicArray`) | `heavymatica-api` (from `26.1.1`) |
| Manhattan (read only) | `F:\workflow\Manhattan` | `midtown-impl` |

**Build:** run `build.bat` from **PowerShell with the sandbox disabled**, e.g.
`cmd /c "F:\workflow\Heavymatica\build.bat --console=plain -q"`. Plain `gradlew` from Bash fails (Java 17 picked up,
and the JDK's loopback pipe breaks under long temp paths). `build.bat` uses the portable JDK 25 in
`F:\workflow\Manhattan\tools\jdk-25*` and a short `tmp\` folder; copy SchematicArray's `build.bat` pattern.

**Compiling against Litematica Array:** it is not on a maven. Heavymatica's `build.gradle` has
`compileOnly fileTree(dir: 'libs', include: '*.jar')`; `libs/` is gitignored and holds the
`litematica-array-*.jar` built from the `heavymatica-api` worktree (`build\libs\`). README explains this.

## Package map (`dev.heavymatica`)

```
core/        pure Java, no Minecraft, JUnit 5 tested
  Box3i, RegionRole, HeavymaticRegion, HeavymaticSpec, Rotation, Mirror, ArrayConfig, Transform,
  VoxelGrid, VoxelToBoxDecomposer, HeavymaticJson, ManhattanScript
place/       placements in the world (Minecraft side)
  HeavymaticPlacement, PlacementStore, PresetLibrary, WorldKey
ui/          malilib screens and list rows
  HeavymaticListEntries (implements schemarray ListEntryProvider), WidgetHeavymaticRow,
  GuiHeavymaticConfig, GuiPresetBrowser, CreateFromSelection, ShapeGeneratorAction
render/      WireframeRenderer, HazardHighlighter, cutaway/CutawayMask, cutaway/CutawayState,
             mixin/SectionCompilerMixin (vanilla only)
manhattan/   ManhattanBridge (reflection), SyncState
export/      TerrainScanner (budgeted), LlmExport, FolderExport, FolderExportText
HeavymaticaMod.java  client entrypoint; each package exposes one static init() that the mod calls
```

## Core model (`dev.heavymatica.core`)

```java
/** Inclusive block box, always normalized (min <= max on every axis). */
public record Box3i(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static Box3i of(int x1, int y1, int z1, int x2, int y2, int z2);   // any corner order
    public long volume();
    public Box3i offset(int dx, int dy, int dz);
    public boolean intersects(Box3i o);
    public int[] toArray();               // {minX,minY,minZ,maxX,maxY,maxZ}: the Manhattan box format
}

public enum RegionRole { DIG, CLEAR, FLOOD, FILL, PROTECT, CUSTOM;   // names match ManhattanAreas.Role
    public int defaultColor();            // ARGB: DIG orange, CLEAR yellow, FLOOD blue, FILL brown, PROTECT green, CUSTOM white
    public boolean cutsTerrain();         // DIG and CLEAR
}

public record HeavymaticRegion(String name, RegionRole role, int color, List<Box3i> boxes) {}   // boxes local to the origin
public record HeavymaticSpec(String name, String author, String description, List<HeavymaticRegion> regions) {
    public Box3i bounds();   public int boxCount();
}

public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }
public enum Mirror { NONE, FRONT_BACK, LEFT_RIGHT }   // FRONT_BACK flips X, LEFT_RIGHT flips Z (Litematica's meaning)
public record ArrayConfig(int copiesX, int copiesY, int copiesZ, int gapX, int gapY, int gapZ) {   // copies >= 1
    public static final ArrayConfig NONE = new ArrayConfig(1, 1, 1, 0, 0, 0);
}
/** Mirror first, then rotate about the local origin, then translate to origin; array steps = transformed bounds size + gap. */
public record Transform(int originX, int originY, int originZ, Rotation rotation, Mirror mirror, ArrayConfig array) {
    public List<Box3i> apply(List<Box3i> local);   // world boxes of ALL array copies, normalized
    public List<List<Box3i>> applyPerCopy(List<Box3i> local);
}

/** Read-only voxel view, so the decomposer does not depend on schemarray classes. */
public interface VoxelGrid { int sizeX(); int sizeY(); int sizeZ(); boolean filled(int x, int y, int z); }
public final class VoxelToBoxDecomposer { public static List<Box3i> decompose(VoxelGrid g); }   // exact cover, no overlaps

public final class HeavymaticJson {         // Gson; format below; unknown fields ignored, "version" checked
    public static String write(HeavymaticSpec s);  public static HeavymaticSpec read(String json);
}
public final class ManhattanScript {        // the text the export / clipboard uses
    public static String areaBoxLines(String areaName, List<Box3i> worldBoxes);   // area_box("n", pos(x,y,z), pos(x,y,z))
    public static String roleCall(RegionRole role, String areaName);              // e.g. area("n")\nclear_area()
}
```

**Settled conventions (implemented, tested):**
* Mirror is about the bounding box of the whole input (`x -> minX+maxX-x` for FRONT_BACK, same on z for
  LEFT_RIGHT); then rotation about local (0,0,0): CLOCKWISE_90 `(x,z) -> (-z,x)`, CLOCKWISE_180 `(-x,-z)`,
  COUNTERCLOCKWISE_90 `(z,-x)`; then + origin; then the array. Copy index `(ix*copiesY + iy)*copiesZ + iz`, copy 0 =
  the original. `Transform.identity()` exists; null rotation/mirror/array mean NONE.
* **Presets are stored normalized: the spec's bounds start at (0,0,0).** Anything that creates a spec (from a
  selection, from the Shape Generator, from JSON import) shifts its boxes so `bounds().min == (0,0,0)` and puts the
  shift into the placement's origin. Then the origin is the preset's corner, as in Litematica.
* `Transform.applyPerCopy` throws `IllegalArgumentException` when a gap makes an array step < 1 on an axis with more
  than one copy: the UI clamps the gap instead (min gap = 1 - size).
* `VoxelToBoxDecomposer` is greedy (X, then Z, then Y); a radius-64 sphere gives ~5500 boxes in ~30 ms. Manhattan
  accepts at most `ManhattanAreas.MAX_BOXES` = 4096 boxes per area: more -> refuse the send with a clear message.

`.heavymatic` format (`config/heavymatica/presets/<name>.heavymatic`):

```json
{ "version": 1, "name": "canal_segment", "author": "", "description": "",
  "regions": [ { "name": "trench", "role": "DIG", "color": -23296, "boxes": [[0,0,0,20,14,20]] } ] }
```

## Placement model (`dev.heavymatica.place`)

`HeavymaticPlacement`: `UUID id`, `String presetName`, `HeavymaticSpec spec` (a copy), `Transform transform`,
`boolean enabled`, `CutawayMode cutaway` (`ON`, `WIREFRAME`, `OFF`), `Map<String, Boolean> regionVisible`,
`String manhattanName` (default = preset name), `long lastSentHash` (0 = never sent), `boolean outOfSync`.
`worldBoxes(regionName)` / `allWorldBoxes()` come from `transform.apply(...)`. The list-entry id is
`"heavymatica:" + id`. Its name in Manhattan is `manhattanName` for a single-region preset and
`manhattanName + "_" + regionName` for each region of a multi-region preset (one Manhattan area per region, all array
copies in it; decision 8).

`PlacementStore`: the placements of the current world, saved to `config/heavymatica/placements/<WorldKey>.json`
(WorldKey = singleplayer save folder name, or server address, sanitized), loaded on join, saved on change. Fires a
change event that the renderer and the cutaway listen to (`PlacementStore.addListener(Runnable)`).

## Litematica Array extension point (`dev.schemarray.api`, in the `heavymatica-api` worktree)

```java
public final class SchematicArrayApi {
    public static final int API_VERSION = 1;
    public static void registerListEntries(ListEntryProvider provider);
    public static void registerShapeAction(String buttonLabel, String hoverText, java.util.function.Consumer<ShapeActionContext> action);
    public static void refreshList();                 // = ListRefresh.later()
    public static String folderOf(String entryId);    // null = top level
    public static int colorOf(String entryId);        // ARGB row tint, or 0 = none
    public static java.util.Set<String> selectedEntryIds();
}
public interface ListEntryProvider { java.util.Collection<? extends ListEntry> entries(); }
public interface ListEntry {
    String id();     // stable and unique, "<modid>:<...>"
    String name();   // sorting and search
    fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase<?> createWidget(int x, int y, int width, int height, boolean isOdd, int listIndex);
    default java.util.List<String> searchStrings() { return java.util.List.of(name().toLowerCase(java.util.Locale.ROOT)); }
    boolean isRenderingEnabled();               // folder hide/show uses these two, like setRenderSchematic
    void setRenderingEnabled(boolean enabled);
}
public record ShapeActionContext(dev.schemarray.shape.VoxelResult voxels, String suggestedName,
                                 net.minecraft.core.BlockPos suggestedOrigin) {}
```

Rules for the extension: existing placements keep their folder/color data (keyed by placement hash id, unchanged
file format); entries are keyed by their `id()` in the same maps. Folders, colors, multi-select and drag & drop must
treat entry rows exactly like placement rows. Array copies, the placement config screen and Litematica's own buttons
stay placement-only. A shape action becomes one extra button on the Shape Generator screen next to the save controls.
Version goes 1.4.0 -> 1.5.0.

## Manhattan bridge (`dev.heavymatica.manhattan`)

`ManhattanBridge.available()` = `FabricLoader.isModLoaded("baritonequeue")` and the class
`dev.baritonequeue.api.ManhattanAreas` exists with `API_VERSION >= 1`; all methods via cached reflection
(`put`, `get`, `names`, `remove`, `selection`, `protectedAreas`, `addListener` with a `BiConsumer` lambda). Enums are
passed by name (`Enum.valueOf(roleClass, role.name())`). Results come back as our own enum
`SendResult { OK, SCRIPT_OWNED, IN_USE, INVALID, NOT_IN_WORLD, UNAVAILABLE }`. Every call happens on the client thread.

Send flow: count the boxes; > 64 -> confirm dialog. `put(..., false)`; on `SCRIPT_OWNED` -> confirm "replace the
script's area?" -> `put(..., true)`; on `IN_USE` -> message "<name> is in use by a running script, stop it first";
on `OK` -> store `lastSentHash = hash(world boxes + role)`, clear `outOfSync`. Any change to the placement sets
`outOfSync` when `lastSentHash != 0`. The listener sets `outOfSync` when a name we own is rewritten with owner
`SCRIPT` or `MANUAL`, or deleted.

## Rendering (`dev.heavymatica.render`)

* **Wireframes:** per visible region, role color (or the region's color), every box, drawn the same way Manhattan
  draws its selection on 26.1.1: `LevelRenderEvents.BEFORE_GIZMOS` + `Gizmos.cuboid(AABB, GizmoStyle.stroke(argb, 2f))`
  inside `mc.levelRenderer.collectPerFrameGizmos()` (see `F:\workflow\Manhattan\src\main\java\dev\baritonequeue\Areas.java`).
  Skip boxes more than 160 blocks from the player.
* **Hazard highlights:** water and lava cells inside `DIG` boxes, found by a budgeted scan (shared with the export
  scanner), drawn as small blue / red cubes; at most 2000 drawn. Skipped for placements with `cutaway == ON`: there the
  cutaway keeps fluids (a cut block becomes `fluidState.createLegacyBlock()`), so the real water / lava is drawn.
* **Cutaway:** `CutawayMask` keeps, per chunk section (`SectionPos.asLong`), the world boxes of visible `DIG`/`CLEAR`
  regions of placements with `cutaway == ON`. On any placement change: mark (old sections ∪ new sections) dirty with
  `LevelRenderer.setSectionDirty(sx, sy, sz)`. A mixin on the mesher's block snapshot makes block (and fluid) lookups
  **for meshing only** inside those boxes return only their fluid (air, water or lava): `CutawayRegionMixin` on vanilla's `RenderSectionRegion`, or,
  with Sodium, `SodiumCutawayMixin` (`@Pseudo`) on Sodium's `LevelSlice#getBlockState(III)` / `#getBlockEntity(III)`,
  which all its other lookups delegate to. Sodium overrides the private `LevelRenderer.setSectionDirty(.., boolean)`,
  so the same dirty marking works for both. A key (default unbound) cycles ON -> WIREFRAME -> OFF for the selected placement.

## Export (`dev.heavymatica.export`)

`TerrainScanner`: scans the world boxes of a placement, at most 20 000 blocks per client tick, only loaded chunks;
result per region as in decision 10, plus an `overlapsProtected` flag from `ManhattanBridge.protectedAreas()`.
`LlmExport`: header (name, world bounds, volume, scan summary per region), then `ManhattanScript.areaBoxLines(...)`
per region, then a suggested call per role. Copies to the clipboard and saves `config/heavymatica/exports/<name>.txt`.
`FolderExport` (registered with `SchematicArrayApi.registerFolderAction`, the "Export" button on folder rows): maps the
folder's `entryIds()` to placements, scans them one after another with `TerrainScanner` (one job at a time; a disconnect
drops it), then `FolderExportText.combine` (pure) writes a `// Folder` header, `// Note:` lines for shared Manhattan area
names, and each placement's `LlmExportText` block separated by a blank line. Saved to `exports/<folder name>.txt`.

## Work packages

| ID | Package | Model | Needs | Done when |
|---|---|---|---|---|
| P0 | Scaffold: Gradle/Loom setup copied from SchematicArray 26.1.1, `build.bat`, `fabric.mod.json` (id `heavymatica`, depends `litematica`, `malilib`, `schemarray >=1.5.0`; suggests `baritonequeue`), mixin json, empty `HeavymaticaMod`, `.gitignore`, README stub, JUnit wired | Sonnet | – | `build.bat` passes |
| P1 | Litematica Array extension point (`dev.schemarray.api`) | Sonnet | – | builds; existing behaviour unchanged; a dummy provider in a test build shows rows |
| P2a | `Box3i`, `Rotation`, `Mirror`, `ArrayConfig`, `Transform`, `VoxelGrid`, `VoxelToBoxDecomposer` + JUnit | Sonnet | P0 | tests pass |
| P2b | `RegionRole`, `HeavymaticRegion`, `HeavymaticSpec`, `HeavymaticJson`, `ManhattanScript` + JUnit | Haiku | P0, P2a's `Box3i` | tests pass |
| P3 | `place/`: placement, store, preset library, world key | Sonnet | P2 | builds |
| P4 | `ui/`: list rows, config screen, preset browser, create-from-selection, shape action | Sonnet | P1, P3 | builds |
| P5 | `render/`: wireframes, hazards, cutaway + mixin | Sonnet | P3 | builds |
| P6a | `manhattan/`: bridge + send flow + sync state | Sonnet | P3 | builds |
| P6b | `export/`: scanner + LLM export | Sonnet | P2b, P3, P6a's bridge signature | builds |
| P7 | README (install, build, usage), docs check | Haiku | all | – |
