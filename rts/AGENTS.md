# Desert Order — Agent & Architecture Guide

This document is the source of truth for the `rts/` project. It must be updated
whenever a major change is made.

---

## 1. Vision

A large-scale, 3D-isometric real-time strategy game modelled on *Desert Order Online*,
built because the original is pay-to-win and hostile to its own players. The goal is
full mechanical parity — the same economy, the same upgrade curves, the same
specialisation-driven combat — with none of the monetisation, plus modernised visuals
and a simulation that can be paced to fit a single sitting.

Non-goals, stated explicitly:

- **Not** a classic RTS. There is no fog of war. Bases are persistent installations
  managed through a panel, not buildings you place with a mouse on open ground.
- **Not** a reskin. No assets, code, or artwork are taken from Desert Order. All
  artwork here is drawn procedurally from primitives at runtime.

---

## 2. Architecture decisions

| Decision | Choice | Why |
| --- | --- | --- |
| Language | TypeScript, strict | The stat tables are large and interlocking; types catch balance-data mistakes at compile time. |
| Build tool | Vite (`vanilla-ts`) | Instant HMR, tiny config, native ESM. |
| Rendering | Canvas 2D | The isometric scene is thousands of flat polygons, not a 3D scene. Canvas 2D with chunk caching hits 60fps and avoids a WebGL dependency. |
| Simulation | Fixed timestep, 20 Hz | Deterministic. Identical results from identical seed + command list. |
| Randomness | Seeded `mulberry32` only | `Math.random()` is banned in simulation code — it would break determinism. |
| Input model | Command pattern | Human and AI both emit `Command` objects into one funnel. This is the seam a server would sit on. |
| Multiplayer | Not built, but structurally ready | State is fully serialisable; commands are the only mutation path. |

### Why the command pattern matters

`applyCommand(ctx, cmd)` in `sim/commands.ts` is the **only** function permitted to
mutate game state in response to intent. The UI never touches `state` directly. This
means adding an authoritative server later requires shipping `Command` objects over a
socket and replaying them — not a rewrite.

---

## 3. Module map

```
rts/
├── index.html
├── vite.config.ts          Dev server bound 0.0.0.0, allowedHosts for proxied preview
└── src/
    ├── app.ts              Application shell: canvas, render loop, UI↔sim glue
    │
    ├── data/               Pure data + pure functions. No state, no DOM.
    │   ├── types.ts        Core vocabulary: Resources, Domain, Specialisation, defs
    │   ├── units.ts        Full unit roster transcribed from the reference table
    │   ├── buildings.ts    Building catalogue + all upgrade cost/output curves
    │   ├── research.ts     Army-wide research lines and their curves
    │   ├── bases.ts        The six base archetypes
    │   ├── combat.ts       The 5x/50x specialisation damage model
    │   └── config.ts       Tuning constants + game-speed presets
    │
    ├── core/               Engine primitives
    │   ├── rng.ts          Seeded RNG + value noise (fBm)
    │   ├── state.ts        The serialisable GameState and its accessors
    │   └── worldmap.ts     Procedural 512×512 terrain, rail network, decorations
    │
    ├── sim/                The simulation. Pure logic, no DOM.
    │   ├── loop.ts         Fixed-timestep driver
    │   ├── commands.ts     Command type + applyCommand — the single mutation funnel
    │   ├── world.ts        Match setup, base founding, defeat conditions
    │   ├── economy.ts      Income, storage, power, slots
    │   ├── production.ts   Construction, unit queues, research
    │   ├── movement.ts     Domain-aware A* pathing, fuel, resupply
    │   └── combat.ts       Targeting, damage resolution, turrets
    │
    ├── ai/
    │   └── opponent.ts     AI that plays by the same rules via Commands only
    │
    ├── render/
    │   ├── camera.ts       Isometric projection + inverse, culling
    │   ├── palette.ts      All colour and art direction
    │   ├── terrain.ts      Chunk-cached terrain rasteriser
    │   ├── sprites.ts      Procedural unit artwork
    │   ├── structures.ts   Procedural base/building artwork
    │   └── scene.ts        Depth-sorted scene composition
    │
    └── ui/
        ├── style.css       Design system
        ├── format.ts       Number/duration formatting
        ├── hud.ts          Top bar + event log
        ├── basepanel.ts    Base/Units/Research/Intel tabs
        ├── minimap.ts      Baked-terrain strategic minimap
        ├── selectionbar.ts Grouped selection summary
        ├── input.ts        Mouse/keyboard → intents
        └── setup.ts        Pre-match dialog + result banner
```

**File size rule: every file stays under 500–600 lines.** Split aggressively rather
than letting any module become a god file.

---

## 4. Data models

### Resources

Three: `steel`, `aluminum`, `fuel`. Steel and aluminium are build costs; **fuel is
consumed per second while a unit is moving**, never at build time. This is taken
directly from the reference sheet.

### Units

`UnitDef` carries the verbatim columns from the source table (steel, aluminum, damage,
armor, range, fuel). Three columns the original does not publish are **derived**, not
invented:

- `hp` = `WEIGHT_HP[class] + armor × 4.5`
- `speed` = `WEIGHT_SPEED[class] × DOMAIN_SPEED[domain]`
- `buildTime` = `12 + (totalCost/1000)^0.62` — logarithmic, so a 35M destroyer is slow
  but not unplayable.

Deriving rather than hardcoding means the numbers stay self-consistent as units are
tuned.

### The combat model — the most important rule in the game

From the reference sheet, verbatim:

> When a unit has special abilities, then its weapons are 5X stronger against this
> specialization and 5X weaker against everything else. (Units good against bases 50x
> weaker)

Implemented in `data/combat.ts`:

- Matching specialisation → **×5**
- Non-matching → **×0.2**
- Non-matching, anti-base unit → **×0.02**
- No offensive specialisation → **×1** (generalists always deal listed damage)

Armour uses diminishing returns, `1 − armor/(armor + 2500)`, rather than flat
subtraction — so a Maus is extremely durable without being literally immune.

Additional rules: submarines attack boats only; ground units cannot hit aircraft
without an AA specialisation; stealth units are untargetable and invisible unless the
viewer fields a detector or has radar coverage.

### Upgrade curves

| Curve | Rate | Effect |
| --- | --- | --- |
| `COST_GROWTH` | 1.75 | Cost of level N = base × 1.75^(N−1) |
| `OUTPUT_GROWTH` | 1.55 | Output of level N = base × 1.55^(N−1) |
| `TIME_GROWTH` | 1.38 | Build time grows slowest |

Output grows slower than cost **on purpose**: this is what makes turtling on one base
lose to expansion, which is the strategic core of Desert Order.

### Bases

Six archetypes, each the exclusive producer of its domain: Home, Tank, Helicopter,
Harbour (coastal only), Air, Train. The Command Base level caps every other structure's
level in that base.

---

## 5. AI abstraction

`AiOpponent` in `ai/opponent.ts` **cannot cheat**. It has no privileged information, no
resource bonuses, and no direct state access — it returns `Command[]` exactly like the
player's UI does. Its strength comes from:

1. A priority-ordered economic build order
2. `enemyComposition()` — tallies what the enemy actually fields, then weights unit
   choice toward counters, scaled by `counterplay`
3. Strength comparison before committing to an attack

Three difficulties (`recruit`/`officer`/`general`) vary think interval, military
spending ratio, attack threshold, and counterplay quality.

---

## 6. Game speed

Desert Order's real pacing has upgrades taking hours. That is authentic but unplayable
in a session, so speed is a first-class setting. Every duration is divided by the
multiplier and income multiplied by it:

| Preset | Multiplier | Use |
| --- | --- | --- |
| Authentic | ×1 | Original pacing |
| **Fast (default)** | ×12 | Full match in about an hour, same balance |
| Blitz | ×45 | Testing army compositions |

---

## 7. Performance notes

The map is 512×512 = 262,144 tiles. Naive rendering is impossible. Mitigations:

- **Terrain chunk cache** — 32×32 blocks rasterised once to offscreen canvases, blitted
  thereafter. Cache is bounded to ~220 chunks and invalidated on quantised zoom change.
- **Typed arrays** for terrain/height/rail. An array-of-objects would cost tens of MB.
- **Viewport culling** on every drawable.
- **Decoration thinning** when zoomed out.
- **Coarse A\*** — long paths search a 2- or 4-tile lattice; aircraft skip search
  entirely and fly straight.
- **Panel throttling** — the base panel rebuilds 4×/second, not 60.
- **Minimap terrain baked once** via `createImageData` pixel writes.

---

## 8. How to run and build

```bash
cd rts
npm install
npm run dev      # dev server on 0.0.0.0:5173
npm run build    # type-check + production bundle to dist/
npm run preview  # serve the production build
```

`npm run build` runs `tsc` first, so **a type error fails the build**.

---

## 9. Controls

| Input | Action |
| --- | --- |
| Left-drag | Marquee-select units |
| Left-click | Select unit or base |
| Right-click | Move / attack / claim territory |
| Right-drag | Pan |
| WASD / arrows | Pan |
| Scroll | Zoom (cursor-anchored) |
| Space | Pause |
| Ctrl+A | Select entire army |
| Esc | Deselect |
| `` ` `` | Cycle game speed |

---

## 10. Current progress

**Complete and working:**

- Full data layer — ~65 units across all five domains, 15 buildings, 8 research lines,
  6 base types, all with real costs and curves
- Exact 5x/50x specialisation combat model with stealth, detectors, armour DR
- Procedural 512×512 world: terrain, coastlines, rail network, decorations, base sites
- Deterministic fixed-timestep simulation with seeded RNG
- Economy: income, storage ceilings, power brownouts, unit slots, territory bonuses
- Construction, parallel unit queues, army-wide research
- Domain-aware pathfinding, fuel consumption, resupply and repair
- Combat: preference-weighted targeting, base damage, defence turrets
- AI opponent with three difficulties, playing via commands only
- Isometric renderer with chunk caching, procedural unit and building art
- Full UI: HUD, four-tab base panel, minimap, selection bar, setup screen
- Configurable game speed
- Allies and enemies, team-based victory conditions

**Not yet built:**

- Multiplayer transport (architecture is ready; no socket layer)
- Save/load (state is serialisable; no persistence UI)
- Sound
- Ammunition-support mechanics for the MP1/MP4/Ammunition Transport units — they exist
  in the roster but currently have no resupply behaviour wired up
- Rail units cannot yet lay new track

---

## 11. Relationship to the rest of this repository

`rts/` is entirely self-contained and shares nothing with the Android application at
the repository root. It has its own `package.json`, its own toolchain, and is not part
of the Android CI workflow. The two projects can be developed independently.
