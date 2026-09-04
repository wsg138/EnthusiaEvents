from pathlib import Path

path = Path("src/main/java/org/enthusia/events/event/EventSessionHardeningListener.java")
text = path.read_text(encoding="utf-8")
replacements = {
    "@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)\n    public void onBedWarsBlockPlace(BlockPlaceEvent event)":
        "@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)\n    public void onBedWarsBlockPlace(BlockPlaceEvent event)",
    "@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)\n    public void onBedWarsBucketEmpty(PlayerBucketEmptyEvent event)":
        "@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)\n    public void onBedWarsBucketEmpty(PlayerBucketEmptyEvent event)",
}
for old, new in replacements.items():
    if text.count(old) != 1:
        raise SystemExit(f"Expected one match, found {text.count(old)} for {old!r}")
    text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("Applied early BedWars spawn-zone guards.")
