# In-game test list (v0.1.0, MC 26.1.1)

Nothing below has been run in the game yet; all unit tests pass (`build.bat`).

**Install (with Minecraft closed):** into the 26.1.1 profile's `mods/`:

* `F:\workflow\Heavymatica\build\libs\heavymatica-0.1.0+mc26.1.1.jar`
* `F:\workflow\SchematicArray-heavymatica-api\build\libs\litematica-array-1.5.0+mc26.1.1.jar` (replaces 1.4.0)
* Manhattan from `midtown-impl` (has `ManhattanAreas`), for the Manhattan part.

Tick each box; note anything odd next to it.

## 1. Litematica Array 1.5.0 still works on its own

- [ ] Placement list, folders, colors, drag and drop, Move, Ctrl/Shift select behave as in 1.4.0.
- [ ] Array copies still hidden from the list; Configure screen and Litematica row buttons only on placements.
- [ ] Restart: folders and colors are kept; `config/schemarray/folders.json` still has the old placement keys.

## 2. Creating heavymatics (key H)

- [ ] H opens the Heavymatica menu (only when no screen is open).
- [ ] "New from Litematica selection" with a 2-box selection: name + role dialog, the placement lands exactly on the selection.
- [ ] "New from Manhattan selection" (with Manhattan, `#sel`/`select(...)` done): same, lands exactly on it.
- [ ] Place a preset from the list at your position; Delete needs Shift; paging with many presets.
- [ ] Shape Generator, "Save as Heavymatic" (right of Peek): a sphere saves and places at the preview; the box count is shown; > 64 warns; > 4096 refuses. *Known: the generator's preview is gone afterwards.*

## 3. `[H]` rows in the placement list (M, Schematic placements)

- [ ] `[H]` row appears with name and "N regions, M boxes".
- [ ] Folders, drag and drop, multi-select, right-click color, search (also by role name) work on `[H]` rows, also mixed with placements.
- [ ] Hiding a folder hides its heavymatics; showing it brings back only those it hid.
- [ ] Enabled/Disabled, `Cut:` cycles ON / WIREFRAME / OFF, Remove only with Shift.
- [ ] Restart the game: placements, folders and colors of `[H]` rows are kept.

## 4. Config screen (Configure)

- [ ] Origin fields, -/+ (Shift = 10), Move to player.
- [ ] Rotate and Mirror match what you expect from Litematica (try an L-shaped preset).
- [ ] Array copies/gaps: copies line up, a very negative gap is clamped (never a crash), also after rotating.
- [ ] Region Visible toggles; box count turns orange above 64, red above 4096.
- [ ] Layout at GUI scale 2 and 4.

## 5. Drawing

- [ ] Wireframes in the role color (dig orange, clear yellow, flood blue, fill brown, protect green); WIREFRAME and ON draw, OFF and Disabled do not; gone beyond ~160 blocks.
- [ ] Water and lava inside a DIG region show as blue / red cubes; they update after a move or within ~10 s; no lag on a large region.
- [ ] **Cutaway ON on a DIG box in a hillside:** the terrain inside disappears, the walls and floor show their faces, no holes at chunk borders. Moving the placement or switching the mode re-draws old and new spots.
- [ ] **Most important:** walk into a cut-away spot: you do NOT fall (the blocks are still there). F3 shows the real block. Midtown pathing / `clear_area` through it still sees and mines the real blocks.
- [ ] Chests inside a cut area: they vanish from view (expected). Lighting of the exposed walls: note if it looks odd.
- [ ] With Sodium (optional): one chat note, no crash, wireframes still drawn.
- [ ] Leave and rejoin the world: no leftover cutaway or cubes.

## 6. Manhattan

- [ ] Without Manhattan: no Send button, no errors in the log.
- [ ] Send a one-region preset: chat "Sent ...", the area shows in Manhattan's Areas panel with role and owner, row says "sent".
- [ ] A PROTECT region becomes a safe area: Midtown paths around it (also after a restart).
- [ ] Multi-region preset: one area per region, named `name_region`; an array is one area with all copies.
- [ ] > 64 boxes: the confirm dialog; No sends nothing.
- [ ] A script did `area_box("same_name", ...)`: Send asks to replace it; No leaves the row unsent.
- [ ] Send while a running script has that area selected: "in use by a running script".
- [ ] After sending, move the placement: row says "out of sync"; send again clears it.
- [ ] After sending, run a script that `area_box`es the same name: Manhattan warns in chat, the row turns "out of sync".
- [ ] Two dialogs in a row (> 64 boxes, then script-owned) both show and return to the screen you came from.

## 7. Export for Manhattan / LLM

- [ ] Export: "Scanning ...", the button shows a %, then "Copied ... also saved to config\heavymatica\exports\<name>.txt".
- [ ] Paste it into a Manhattan script: it parses as-is.
- [ ] The counts look right (water, lava, top blocks, falling blocks at the edges, lava next to the region, overlap with a protected area).
- [ ] A placement partly in unloaded chunks: "% unscanned" above 0, the export still finishes.
- [ ] Disconnect during a scan: no message, no file.
