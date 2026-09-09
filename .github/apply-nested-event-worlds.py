from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace(path: str, old: str, new: str, count: int = 1) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        raise SystemExit(f"{path}: expected {count} occurrence(s), found {found}: {old[:120]!r}")
    target.write_text(text.replace(old, new, count), encoding="utf-8")


# maps.yml: migrate legacy root names before loading, but still resolve aliases at runtime
# if a backup write fails or a server is intentionally mixed during migration.
replace(
    "src/main/java/org/enthusia/events/event/MapSetupService.java",
    "import org.enthusia.events.util.LocationCodec;\n",
    "import org.enthusia.events.util.EventWorldPaths;\nimport org.enthusia.events.util.LocationCodec;\n",
)
replace(
    "src/main/java/org/enthusia/events/event/MapSetupService.java",
    "        ensureMapsFile();\n        load();\n",
    "        ensureMapsFile();\n        EventWorldPaths.migrateMapsFile(file, plugin.getLogger());\n        load();\n",
)
replace(
    "src/main/java/org/enthusia/events/event/MapSetupService.java",
    "                map.worldName(mapSection.getString(\"world\"));\n",
    "                map.worldName(EventWorldPaths.resolveExistingWorldName(mapSection.getString(\"world\")));\n",
)
replace(
    "src/main/java/org/enthusia/events/event/MapSetupService.java",
    "                            regionSection.getString(\"world\", map.worldName()),\n",
    "                            EventWorldPaths.resolveExistingWorldName(regionSection.getString(\"world\", map.worldName())),\n",
)
replace(
    "src/main/java/org/enthusia/events/event/MapSetupService.java",
    "                    area.getString(\"world\", map.worldName()),\n",
    "                    EventWorldPaths.resolveExistingWorldName(area.getString(\"world\", map.worldName())),\n",
)

# Location decoding must load the resolved nested world so every stored spawn, point,
# generator, chest and checkpoint is attached to the same Bukkit World instance.
replace(
    "src/main/java/org/enthusia/events/util/LocationCodec.java",
    "import org.bukkit.Bukkit;\n",
    "",
)
replace(
    "src/main/java/org/enthusia/events/util/LocationCodec.java",
    "import org.bukkit.WorldCreator;\n",
    "",
)
replace(
    "src/main/java/org/enthusia/events/util/LocationCodec.java",
    "        World world = Bukkit.getWorld(parts[0]);\n        if (world == null) {\n            world = Bukkit.createWorld(new WorldCreator(parts[0]));\n        }\n",
    "        World world = EventWorldPaths.loadWorld(parts[0]);\n",
)

# New transfers/exports should be created under Events/, while sanitizer only permits
# one controlled child path rather than arbitrary slash traversal.
replace(
    "src/main/java/org/enthusia/events/event/MapCopyService.java",
    "import org.enthusia.events.util.LocationCodec;\n",
    "import org.enthusia.events.util.EventWorldPaths;\nimport org.enthusia.events.util.LocationCodec;\n",
)
replace(
    "src/main/java/org/enthusia/events/event/MapCopyService.java",
    "        String base = \"Events-\" + displayEventName(map.eventType());\n",
    "        String base = EventWorldPaths.eventWorldName(displayEventName(map.eventType()));\n",
)
replace(
    "src/main/java/org/enthusia/events/event/MapCopyService.java",
    "    private String sanitizeWorldName(String worldName) {\n        String sanitized = Optional.ofNullable(worldName).orElse(\"\")\n                .replaceAll(\"[^A-Za-z0-9_\\\\-]\", \"-\")\n                .replaceAll(\"-{2,}\", \"-\")\n                .replaceAll(\"^-|-$\", \"\");\n        return sanitized.isBlank() ? \"Events-Map\" : sanitized;\n    }\n",
    "    private String sanitizeWorldName(String worldName) {\n        return EventWorldPaths.sanitizeWorldName(worldName);\n    }\n",
)

# Multiverse auto-registration and event-world-only BedWars polish must also recognize
# worlds whose Bukkit name contains the Events/ parent.
replace(
    "src/main/java/org/enthusia/events/event/BedWarsPolishListener.java",
    "import org.enthusia.events.EnthusiaEventsPlugin;\n",
    "import org.enthusia.events.EnthusiaEventsPlugin;\nimport org.enthusia.events.util.EventWorldPaths;\n",
)
replace(
    "src/main/java/org/enthusia/events/event/BedWarsPolishListener.java",
    "    private boolean isEventWorld(World world) {\n        String name = world.getName().toLowerCase(Locale.ROOT);\n        return name.startsWith(\"events-\") || name.startsWith(\"ee_\");\n    }\n",
    "    private boolean isEventWorld(World world) {\n        return EventWorldPaths.isEventWorldName(world.getName());\n    }\n",
)

# Keep deployment/testing guidance aligned with the new storage layout.
replace(
    "TESTING.md",
    "`Events-BedWars: [ \"old\" ]` or numbered worlds like `Events-BedWars-1`.",
    "`Events/-Bedwars: [ \"old\" ]` or numbered worlds like `Events/-Bedwars-1`.",
)

# Restore the normal read-only CI workflow and remove this one-shot materializer in the
# source commit. The currently running workflow continues from its already-loaded definition.
workflow = ROOT / ".github/workflows/quality.yml"
workflow.write_text("""name: Quality

on:
  pull_request:
  push:
    branches:
      - main

permissions:
  contents: read

jobs:
  verify:
    name: Maven verify
    runs-on: ubuntu-latest
    timeout-minutes: 15

    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven

      - name: Compile and run quality checks
        run: mvn --batch-mode --no-transfer-progress verify

      - name: Upload plugin jar
        uses: actions/upload-artifact@v4
        with:
          name: EnthusiaEvents-${{ github.sha }}
          path: target/enthusia-events-1.0.0-SNAPSHOT.jar
          if-no-files-found: error
""", encoding="utf-8")
Path(__file__).unlink()
