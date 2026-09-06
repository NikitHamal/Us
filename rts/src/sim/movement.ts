import type { GameState, Unit } from '../core/state';
import { UNIT_BY_ID } from '../data/units';
import type { WorldMap } from '../core/worldmap';
import { isPassableGround, isWater, isRail, inBounds } from '../core/worldmap';
import { FUEL_TANK_SECONDS, RESUPPLY_RADIUS, REPAIR_RATE } from '../data/config';
import { bonusFor } from './production';
import { MinHeap } from '../core/heap';

/**
 * Movement, pathfinding and fuel.
 *
 * Each domain has different traversal rules, which is a core Desert Order feature:
 * aircraft ignore terrain, boats need water, and rail units are locked to track.
 */

export function canTraverse(map: WorldMap, defId: string, x: number, y: number): boolean {
  const def = UNIT_BY_ID[defId];
  if (!def) return false;
  const ix = Math.floor(x);
  const iy = Math.floor(y);
  if (!inBounds(ix, iy)) return false;
  switch (def.domain) {
    case 'air':
      return true;
    case 'naval':
      return isWater(map, ix, iy);
    case 'rail':
      return isRail(map, ix, iy);
    case 'ground':
    default:
      return isPassableGround(map, ix, iy);
  }
}

/**
 * A* over the tile grid, coarsened for long distances.
 *
 * The map is 512x512, so a naive full-resolution A* across the whole map would visit
 * a quarter of a million nodes. Long paths are searched on a coarse lattice and then
 * refined near the endpoints, which keeps worst-case cost bounded.
 */
export function findPath(
  map: WorldMap,
  defId: string,
  sx: number,
  sy: number,
  tx: number,
  ty: number,
): Array<{ x: number; y: number }> {
  const def = UNIT_BY_ID[defId];
  if (!def) return [];

  // Aircraft fly straight lines; no search needed at all.
  if (def.domain === 'air') return [{ x: tx, y: ty }];

  const start = { x: Math.floor(sx), y: Math.floor(sy) };
  const goal = { x: Math.floor(tx), y: Math.floor(ty) };
  const dist = Math.hypot(goal.x - start.x, goal.y - start.y);
  const step = dist > 90 ? 4 : dist > 35 ? 2 : 1;

  const key = (x: number, y: number) => y * 1024 + x;
  type Node = { x: number; y: number; f: number; g: number };
  const open = new MinHeap<Node>((n) => n.f);
  open.push({ x: start.x, y: start.y, f: dist, g: 0 });
  const cameFrom = new Map<number, number>();
  const gScore = new Map<number, number>([[key(start.x, start.y), 0]]);
  const maxNodes = 6000;
  let visited = 0;
  let best = { x: start.x, y: start.y, h: dist };

  while (open.size > 0 && visited < maxNodes) {
    const cur = open.pop() as Node;
    // Stale heap entry: a cheaper route to this node was already expanded.
    if (cur.g > (gScore.get(key(cur.x, cur.y)) ?? Infinity)) continue;
    visited++;

    const h = Math.hypot(goal.x - cur.x, goal.y - cur.y);
    if (h < best.h) best = { x: cur.x, y: cur.y, h };
    if (h <= step) {
      return reconstruct(cameFrom, key(cur.x, cur.y), goal);
    }

    for (let dy = -1; dy <= 1; dy++) {
      for (let dx = -1; dx <= 1; dx++) {
        if (dx === 0 && dy === 0) continue;
        const nx = cur.x + dx * step;
        const ny = cur.y + dy * step;
        if (!canTraverse(map, defId, nx, ny)) continue;
        const cost = cur.g + Math.hypot(dx, dy) * step;
        const k = key(nx, ny);
        if (cost >= (gScore.get(k) ?? Infinity)) continue;
        gScore.set(k, cost);
        cameFrom.set(k, key(cur.x, cur.y));
        open.push({ x: nx, y: ny, f: cost + Math.hypot(goal.x - nx, goal.y - ny), g: cost });
      }
    }
  }

  // No complete path: move as close as the search managed to get.
  return best.h < dist ? reconstruct(cameFrom, key(best.x, best.y), best) : [];
}

function reconstruct(
  cameFrom: Map<number, number>,
  endKey: number,
  goal: { x: number; y: number },
): Array<{ x: number; y: number }> {
  const path: Array<{ x: number; y: number }> = [];
  let k: number | undefined = endKey;
  while (k !== undefined) {
    path.push({ x: k % 1024, y: Math.floor(k / 1024) });
    k = cameFrom.get(k);
  }
  path.reverse();
  path.push({ x: goal.x, y: goal.y });
  // Drop the starting tile so the unit does not walk backwards to its own centre.
  return path.slice(1);
}

export function effectiveSpeed(state: GameState, unit: Unit): number {
  const def = UNIT_BY_ID[unit.defId];
  if (!def) return 0;
  return def.speed * (1 + bonusFor(state, unit.owner, 'engines'));
}

/** Moves every unit along its path, burning fuel as it goes. */
export function tickMovement(state: GameState, _map: WorldMap, dt: number): void {
  for (const unit of state.units) {
    if (unit.path.length === 0) continue;
    const def = UNIT_BY_ID[unit.defId];
    if (!def) continue;

    if (unit.fuel <= 0) {
      // Out of fuel: the unit is stranded until it is resupplied.
      continue;
    }

    let remaining = effectiveSpeed(state, unit) * dt;
    while (remaining > 0 && unit.path.length > 0) {
      const wp = unit.path[0];
      const dx = wp.x - unit.x;
      const dy = wp.y - unit.y;
      const d = Math.hypot(dx, dy);
      if (d < 0.05) {
        unit.path.shift();
        continue;
      }
      unit.facing = Math.atan2(dy, dx);
      const travel = Math.min(remaining, d);
      unit.x += (dx / d) * travel;
      unit.y += (dy / d) * travel;
      remaining -= travel;
      if (travel >= d - 1e-6) unit.path.shift();
    }

    const efficiency = 1 - bonusFor(state, unit.owner, 'fuel_efficiency');
    unit.fuel = Math.max(0, unit.fuel - def.fuel * Math.max(0.2, efficiency) * dt);
  }
}

/** Refuels and repairs units sitting near a friendly base with a repair bay. */
export function tickResupply(state: GameState, dt: number): void {
  if (state.units.length === 0) return;
  // Group bases by owner once rather than scanning every base for every unit.
  const basesByOwner = new Map<number, typeof state.bases>();
  for (const b of state.bases) {
    const list = basesByOwner.get(b.owner);
    if (list) list.push(b);
    else basesByOwner.set(b.owner, [b]);
  }
  const radiusSq = RESUPPLY_RADIUS * RESUPPLY_RADIUS;

  for (const unit of state.units) {
    const owned = basesByOwner.get(unit.owner);
    if (!owned) continue;
    const base = owned.find((b) => {
      const dx = b.wx - unit.x;
      const dy = b.wy - unit.y;
      return dx * dx + dy * dy <= radiusSq;
    });
    if (!base) continue;
    const bay = base.buildings.find((b) => b.defId === 'repair_bay' && b.level > 0);
    const refuelRate = bay ? 0.25 : 0.08;
    unit.fuel = Math.min(unit.maxFuel, unit.fuel + unit.maxFuel * refuelRate * dt);
    if (bay && unit.hp < unit.maxHp) {
      unit.hp = Math.min(unit.maxHp, unit.hp + unit.maxHp * REPAIR_RATE * (1 + bay.level * 0.1) * dt);
    }
  }
}

/** Fuel capacity is expressed in seconds of movement, converted to litres. */
export function fuelCapacityFor(defId: string): number {
  const def = UNIT_BY_ID[defId];
  return def ? Math.max(1, def.fuel * FUEL_TANK_SECONDS) : 1;
}
