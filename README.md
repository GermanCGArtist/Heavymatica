# Heavymatica

Heavymatica is a client-side Fabric addon for Litematica and Litematica Array that adds heavymatics: named, multi-box area presets (dig, clear, flood, fill, protect) shown in Litematica's placement list, drawn in the world as wireframes with an optional terrain cutaway, and sent to Manhattan (`baritonequeue`) as saved areas. Targets Minecraft 26.1.1.

## Building

Run `build.bat` (from PowerShell: `cmd /c "build.bat --console=plain -q"`). It uses the portable JDK 25 from `tools\` or `..\Manhattan\tools\`, and writes the mod to `build\libs\`.

Litematica Array is not on a maven. Before building, put the `litematica-array-*.jar` built from the `heavymatica-api` branch of Litematica Array into `libs/` (it is gitignored).
