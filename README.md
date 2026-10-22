# Conduits Prevent Drowned

## Installation

Install `conduits_prevent_drowned-<version>.jar` and Fabric API on your Fabric server.
Clients do not need this mod. It also works in singleplayer when installed locally.

## Behavior

A player with **Conduit Power** prevents drowned from spawning within the configured horizontal radius in the same dimension.
The check uses block coordinates, ignores height, and excludes positions exactly on the radius.
Any affected player can provide protection.

Natural spawning and regular mob spawners (including spawner minecarts) are covered.
Existing drowned are not removed. Zombie conversion, commands, spawn eggs and trial
spawners are unchanged, matching the original Fabric mod's two spawn hooks.
The mod checks spawn attempts directly; it does not scan entities every tick.

## Configuration

First startup creates `config/conduits_prevent_drowned.json`:

```json
{
  "preventDrownedInRange": 400
}
```

The range is an integer from **0 to 400**; 0 disables prevention.
400 is a radius, not a global ban: protection still requires a nearby player with Conduit Power.
This normally covers the natural spawning area around an affected player.

Reload with `/conduits_prevent_drowned reload` (permission level 2 or server console).

Existing configuration files are never rewritten. 
A failed reload retains the previous value; an invalid file on initial startup uses the default range of 400.
A missing file is recreated with defaults.

## Build

Run `./gradlew build` (Windows: `.\gradlew.bat build`) with Java 25.
The installable jar is in `build/libs`; do not install the `-sources.jar`.
`build` includes configuration and text-rendering regression verification.

## License

AGPL-3.0; see [LICENSE](LICENSE). Copyright (c) 2026 SwordfishBE.