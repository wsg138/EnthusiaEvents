# EnthusiaEvents automated testing guide

`TESTING.md` remains the large manual/live-player acceptance checklist. This file documents the repository-local automated test layer added by the test-hardening campaign.

The two layers are intentionally separate:

- **JUnit / Maven** — deterministic parsing, contracts, geometry and other behavior that can be tested honestly without a running server.
- **`TESTING.md` + real-server harness/manual acceptance** — gameplay, inventories, worlds, vehicles, block physics, provider integrations, restart/relog and other behavior that requires real Paper/player interaction.

Sentinel Sim is an additional built-plugin compatibility/simulation layer. It does not replace either of these repository-owned layers.

## Harness

The Maven project now declares JUnit 5 and Maven Surefire 3.5.2. The existing GitHub `Quality` workflow already runs:

```bash
mvn --batch-mode --no-transfer-progress verify
```

That means the new unit tests run automatically before the exact-SHA plugin artifact is uploaded.

## Automated tests added

### `EventTypeTest`

Protects the complete event identifier surface:

- every canonical `EventType` parses in canonical and lower-case form;
- the historical `SPLEEG` spelling remains an alias for `SPLEGG`;
- null, blank and unknown names fail closed;
- whitespace-wrapped names do not get silently accepted unless parsing policy is intentionally changed.

### `AdminCommandSupportTest`

Covers deterministic admin command helpers:

- tab-completion filtering is case-insensitive prefix matching and preserves input order;
- positive integer parsing accepts positive values, clamps zero/negative values to one, and uses the supplied fallback for malformed input;
- silent event parsing supports canonical/legacy event names and returns `null` for invalid values.

### `CuboidRegionTest`

Protects map-region geometry and world validation:

- corner order is normalized using block coordinates;
- the whole maximum block is included while the next block is excluded;
- null locations and wrong-world locations are outside the region;
- missing worlds and cross-world corner selections are rejected.

The test uses a tiny Java proxy for the Bukkit `World` interface so no server is started and no world files are needed.

### `EventSpecAuditRegistryTest`

Requires every `EventType` to have a complete audit/spec entry with nonblank setup, win-condition and reset requirements. It also checks representative kit/loot contracts for SkyWars, Fight and Sumo.

This is valuable because adding a new event enum without adding its setup/win/reset audit contract becomes an immediate CI failure rather than a startup warning discovered later.

## Commands

Full quality gate (same command as GitHub Actions):

```bash
mvn --batch-mode --no-transfer-progress verify
```

Run all JUnit tests only:

```bash
mvn --batch-mode --no-transfer-progress test
```

Focused runs:

```bash
mvn -Dtest=EventTypeTest test
mvn -Dtest=AdminCommandSupportTest test
mvn -Dtest=CuboidRegionTest test
mvn -Dtest=EventSpecAuditRegistryTest test
```

## Results

Maven Surefire writes:

- `target/surefire-reports/*.txt`
- `target/surefire-reports/TEST-*.xml`

The GitHub `Quality / Maven verify` job is the hosted result. When using it as evidence, record the exact PR head SHA and workflow run/job that tested that SHA.

The workflow artifact is a built plugin JAR; it is not itself proof that live gameplay was accepted.

## Reviewing failures

Classify the failure before making changes:

1. **Parsing/contract regression** — a deterministic assertion reached production code and returned the wrong result. Fix the behavior or deliberately revise the contract if requirements changed.
2. **Region failure** — review inclusive/exclusive block bounds and world identity carefully; off-by-one map protection bugs can leak players or permit interactions outside the intended arena.
3. **Event-spec failure** — a new/changed event is missing its setup/win/reset contract. Add the real spec instead of deleting the assertion.
4. **Harness/build failure** — Maven/JUnit/dependency/Java compilation failed before behavior executed. Repair the harness without weakening a behavioral assertion.
5. **PMD failure** — the existing command-quality gate found a static issue; JUnit success does not override it.
6. **Real-server/manual failure** — keep it in the live acceptance layer. A unit test pass does not prove vehicle physics, world reset, inventory restoration or provider integration.
7. **Sentinel/MockBukkit boundary** — do not approximate unsupported server behavior just to obtain green simulation evidence.
8. **Infrastructure failure** — a zero-step/no-runner GitHub failure is not a test pass and does not justify production changes.

## What is still manual/runtime evidence

The existing `TESTING.md` remains authoritative for the broad gameplay matrix, including:

- event join/leave/vote/start/finish flows;
- snapshots, inventory/armor/health restoration and relog recovery;
- map setup/save/clear/export/import behavior;
- block placement/breaking, crafting and item-drop rules;
- BedWars beds, generators, shops and death/resource behavior;
- SkyWars chest/drop/glow behavior;
- boats, horses, elytra and race checkpoints;
- CTF/Capture Players flags, jail/free/capture zones;
- event-specific combat, projectiles and death handling;
- trophy room and payouts;
- private/invite/disabled/autostart behavior;
- integrations such as Vault, PlaceholderAPI, CombatX/CombatLogX, NotBounties and OldCombatMechanics;
- restart/relog and live migration/release gates.

Do not claim those are automated merely because `mvn verify` passes.

## Adding more automated tests

Prefer repository-local JUnit when logic can be isolated deterministically. High-value next candidates include:

- command permission/rejection and tab-completion contracts;
- event-map validation rules;
- vote and map-rotation selection policy;
- kit name normalization and preview assembly;
- payout/ranking calculations;
- disabled/private event eligibility rules;
- snapshot state transformations that can be isolated from Bukkit;
- bounded queue/cooldown/time calculations;
- config defaults and invalid-config fail-closed behavior.

Use MockBukkit only when it faithfully models the Bukkit interaction. Keep the existing real-server harness for behavior involving worlds, entities, vehicles, scheduler timing, physics or provider integrations that simulation cannot prove honestly.

## Security and test data

Never commit production worlds, player inventories, databases, economy balances, credentials, webhooks, tokens or private server configuration. Tests should use generated/fake data and temporary files.

## Worker coordination

Before modifying tests, reconcile live `main`, open PRs and changed paths. This hardening branch is test/documentation-only. If an automated test reveals a real product defect, repair it in the appropriate product branch/PR rather than hiding unrelated runtime changes in the test harness.
