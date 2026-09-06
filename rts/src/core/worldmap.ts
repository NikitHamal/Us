import { Rng, ValueNoise } from './rng';
import { MAP_WIDTH, MAP_HEIGHT } from '../data/config';

/**
 * Procedural generation of the large world map.
 *
 * Terrain is stored in flat typed arrays rather than an object grid: at 512x512 that
 * is 262144 cells, and an array-of-objects would cost tens of megabytes and thrash the
 * garbage collector during rendering.
 */

export const Terrain = {
  DeepWater: 0,
  Water: 1,
  Beach: 2,
  Sand: 3,
  Dunes: 4,
  Rock: 5,
  Mountain: 6,
  Scrub: 7,
} as const;

export type Terrain = (typeof Terrain)[keyof typeof Terrain];

/** Which terrain each movement domain may occupy. */
export const LAND_TERRAIN: Set<number> = new Set([Terrain.Beach, Terrain.Sand, Terrain.Dunes, Terrain.Rock, Terrain.Scrub]);
export const WATER_TERRAIN: Set<number> = new Set([Terrain.DeepWater, Terrain.Water]);

export interface Decoration {
  kind: 'palm' | 'rock' | 'cactus' | 'wreck' | 'ruin';
  x: number;
  y: number;
  /** Per-instance variation so a forest of palms is not visibly tiled. */
  variant: number;
  scale: number;
}

export interface WorldMap {
  width: number;
  height: number;
  terrain: Uint8Array;
  /** Normalised elevation, used for shading. */
  height01: Float32Array;
  /** True where rail track exists; rail units are confined to these tiles. */
  rail: Uint8Array;
  decorations: Decoration[];
  /** Candidate founding sites discovered during generation. */
  sites: Array<{ x: number; y: number; coastal: boolean }>;
}

export function idx(x: number, y: number): number {
  return y * MAP_WIDTH + x;
}

export function inBounds(x: number, y: number): boolean {
  return x >= 0 && y >= 0 && x < MAP_WIDTH && y < MAP_HEIGHT;
}

export function terrainAt(map: WorldMap, x: number, y: number): Terrain {
  if (!inBounds(x, y)) return Terrain.DeepWater;
  return map.terrain[idx(x, y)] as Terrain;
}

export function isLand(map: WorldMap, x: number, y: number): boolean {
  return LAND_TERRAIN.has(terrainAt(map, x, y));
}

export function isWater(map: WorldMap, x: number, y: number): boolean {
  return WATER_TERRAIN.has(terrainAt(map, x, y));
}

export function isRail(map: WorldMap, x: number, y: number): boolean {
  return inBounds(x, y) && map.rail[idx(x, y)] === 1;
}

/** Ground units cannot cross mountains; everything else on land is passable. */
export function isPassableGround(map: WorldMap, x: number, y: number): boolean {
  const t = terrainAt(map, x, y);
  return LAND_TERRAIN.has(t) && t !== Terrain.Mountain;
}

function classify(h: number, moisture: number): Terrain {
  if (h < 0.28) return Terrain.DeepWater;
  if (h < 0.36) return Terrain.Water;
  if (h < 0.395) return Terrain.Beach;
  if (h > 0.78) return Terrain.Mountain;
  if (h > 0.68) return Terrain.Rock;
  if (moisture > 0.62) return Terrain.Scrub;
  if (moisture < 0.34) return Terrain.Dunes;
  return Terrain.Sand;
}

/** Generates the full world. Deterministic for a given seed. */
export function generateWorld(seed: number): WorldMap {
  const rng = new Rng(seed);
  const elevation = new ValueNoise(rng);
  const moistureNoise = new ValueNoise(rng);
  const detail = new ValueNoise(rng);

  const terrain = new Uint8Array(MAP_WIDTH * MAP_HEIGHT);
  const height01 = new Float32Array(MAP_WIDTH * MAP_HEIGHT);
  const rail = new Uint8Array(MAP_WIDTH * MAP_HEIGHT);

  const scale = 5.5;
  for (let y = 0; y < MAP_HEIGHT; y++) {
    for (let x = 0; x < MAP_WIDTH; x++) {
      const nx = x / MAP_WIDTH;
      const ny = y / MAP_HEIGHT;
      let h = elevation.fbm(nx * scale, ny * scale, 5);
      // Radial falloff keeps the landmass away from the map edge and creates coastline.
      const dx = nx - 0.5;
      const dy = ny - 0.5;
      const dist = Math.sqrt(dx * dx + dy * dy) * 2;
      h = h * 1.12 - Math.pow(dist, 2.6) * 0.55;
      h += detail.fbm(nx * scale * 6, ny * scale * 6, 2) * 0.06;
      const m = moistureNoise.fbm(nx * scale * 1.7 + 11, ny * scale * 1.7 + 7, 4);
      const i = idx(x, y);
      height01[i] = h;
      terrain[i] = classify(h, m);
    }
  }

  const map: WorldMap = {
    width: MAP_WIDTH,
    height: MAP_HEIGHT,
    terrain,
    height01,
    rail,
    decorations: [],
    sites: [],
  };

  carveRailNetwork(map, rng);
  map.sites = findSites(map, rng);
  map.decorations = scatterDecorations(map, rng);
  return map;
}

/**
 * Lays a rail network as a set of long, gently-curving trunk lines across the landmass.
 * Rail units are confined to these tiles, so the network doubles as a strategic feature:
 * whoever holds a junction controls where rail artillery can reach.
 */
function carveRailNetwork(map: WorldMap, rng: Rng): void {
  const lines = 7;
  for (let l = 0; l < lines; l++) {
    const horizontal = rng.chance(0.5);
    let x = horizontal ? 8 : rng.int(40, MAP_WIDTH - 40);
    let y = horizontal ? rng.int(40, MAP_HEIGHT - 40) : 8;
    let drift = rng.range(-0.4, 0.4);
    const steps = horizontal ? MAP_WIDTH - 16 : MAP_HEIGHT - 16;
    for (let s = 0; s < steps; s++) {
      drift += rng.range(-0.06, 0.06);
      drift = Math.max(-0.55, Math.min(0.55, drift));
      if (horizontal) {
        x += 1;
        y += drift;
      } else {
        y += 1;
        x += drift;
      }
      const cx = Math.round(x);
      const cy = Math.round(y);
      if (!inBounds(cx, cy)) break;
      // Track only exists on land; a line meeting water simply stops.
      if (!isLand(map, cx, cy)) continue;
      map.rail[idx(cx, cy)] = 1;
      // Widen slightly so diagonal movement does not fall off the track.
      const wx = horizontal ? cx : cx + 1;
      const wy = horizontal ? cy + 1 : cy;
      if (inBounds(wx, wy) && isLand(map, wx, wy)) map.rail[idx(wx, wy)] = 1;
    }
  }
}

/**
 * Finds well-spaced flat locations suitable for founding bases. Coastal sites are
 * flagged so Harbour bases can be restricted to them.
 */
function findSites(map: WorldMap, rng: Rng): WorldMap['sites'] {
  const sites: WorldMap['sites'] = [];
  const minSpacing = 26;
  const attempts = 4000;
  for (let a = 0; a < attempts && sites.length < 60; a++) {
    const x = rng.int(20, MAP_WIDTH - 20);
    const y = rng.int(20, MAP_HEIGHT - 20);
    if (!isPassableGround(map, x, y)) continue;
    if (!areaIsBuildable(map, x, y, 6)) continue;
    if (sites.some((s) => Math.hypot(s.x - x, s.y - y) < minSpacing)) continue;
    sites.push({ x, y, coastal: nearWater(map, x, y, 9) });
  }
  return sites;
}

/** A base needs a contiguous flat patch; mountains and water disqualify a site. */
export function areaIsBuildable(map: WorldMap, cx: number, cy: number, radius: number): boolean {
  for (let y = cy - radius; y <= cy + radius; y++) {
    for (let x = cx - radius; x <= cx + radius; x++) {
      if (!isPassableGround(map, x, y)) return false;
    }
  }
  return true;
}

export function nearWater(map: WorldMap, cx: number, cy: number, radius: number): boolean {
  for (let y = cy - radius; y <= cy + radius; y += 2) {
    for (let x = cx - radius; x <= cx + radius; x += 2) {
      if (isWater(map, x, y)) return true;
    }
  }
  return false;
}

/** Scatters palms, rocks, cacti and wrecks with terrain-appropriate density. */
function scatterDecorations(map: WorldMap, rng: Rng): Decoration[] {
  const out: Decoration[] = [];
  for (let y = 2; y < MAP_HEIGHT - 2; y++) {
    for (let x = 2; x < MAP_WIDTH - 2; x++) {
      const t = terrainAt(map, x, y);
      let chance = 0;
      let kind: Decoration['kind'] = 'rock';
      if (t === Terrain.Scrub) {
        chance = 0.09;
        kind = rng.chance(0.6) ? 'palm' : 'cactus';
      } else if (t === Terrain.Sand) {
        chance = 0.018;
        kind = rng.chance(0.5) ? 'cactus' : 'rock';
      } else if (t === Terrain.Rock || t === Terrain.Mountain) {
        chance = 0.11;
        kind = 'rock';
      } else if (t === Terrain.Beach) {
        chance = 0.012;
        kind = rng.chance(0.7) ? 'palm' : 'wreck';
      } else if (t === Terrain.Dunes) {
        chance = 0.006;
        kind = rng.chance(0.3) ? 'ruin' : 'rock';
      }
      if (chance > 0 && rng.chance(chance) && map.rail[idx(x, y)] === 0) {
        out.push({
          kind,
          x: x + rng.range(0.15, 0.85),
          y: y + rng.range(0.15, 0.85),
          variant: rng.int(0, 3),
          scale: rng.range(0.8, 1.3),
        });
      }
    }
  }
  return out;
}
