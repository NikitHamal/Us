import type { GameState, Unit, PlayerId, EntityId } from '../core/state';
import { pushLog, baseById, playerById, addEffect } from '../core/state';
import type { WorldMap } from '../core/worldmap';
import { UNIT_BY_ID } from '../data/units';
import { findPath, canTraverse, fuelCapacityFor } from './movement';
import { startUpgrade, queueUnit, cancelQueueItem, startResearch } from './production';
import { recomputeBase } from './economy';
import { CLAIM_RADIUS } from '../data/config';
import { foundBase } from './world';

/**
 * The command layer.
 *
 * Nothing outside this module is allowed to mutate game state directly in response to
 * user input. Every intent -- from the human player and from the AI alike -- becomes a
 * Command, and applyCommand is the single funnel. That is precisely the seam a future
 * authoritative server would sit on: ship commands over the wire, replay them in order.
 */

export type Command =
  | { type: 'move'; player: PlayerId; unitIds: EntityId[]; x: number; y: number }
  | { type: 'attackUnit'; player: PlayerId; unitIds: EntityId[]; targetId: EntityId }
  | { type: 'attackBase'; player: PlayerId; unitIds: EntityId[]; baseId: EntityId }
  | { type: 'stop'; player: PlayerId; unitIds: EntityId[] }
  | { type: 'deploy'; player: PlayerId; baseId: EntityId; unitDefId: string; count: number }
  | { type: 'recall'; player: PlayerId; unitIds: EntityId[] }
  | { type: 'build'; player: PlayerId; baseId: EntityId; buildingId: string }
  | { type: 'queueUnit'; player: PlayerId; baseId: EntityId; unitDefId: string; count: number }
  | { type: 'cancelQueue'; player: PlayerId; baseId: EntityId; index: number }
  | { type: 'research'; player: PlayerId; researchId: string }
  | { type: 'claim'; player: PlayerId; unitIds: EntityId[]; territoryId: EntityId }
  | { type: 'foundBase'; player: PlayerId; territoryId: EntityId; kind: string };

export interface CommandContext {
  state: GameState;
  map: WorldMap;
}

/** Applies one command. Invalid commands are ignored rather than throwing. */
export function applyCommand(ctx: CommandContext, cmd: Command): void {
  const { state, map } = ctx;
  const player = playerById(state, cmd.player);
  if (!player || player.defeated) return;

  switch (cmd.type) {
    case 'move':
      for (const u of ownedUnits(state, cmd.player, cmd.unitIds)) {
        orderMove(state, map, u, cmd.x, cmd.y);
      }
      break;

    case 'attackUnit':
      for (const u of ownedUnits(state, cmd.player, cmd.unitIds)) {
        u.order = { kind: 'attack', targetId: cmd.targetId };
        const target = state.units.find((t) => t.id === cmd.targetId);
        if (target) orderMove(state, map, u, target.x, target.y, true);
      }
      break;

    case 'attackBase':
      for (const u of ownedUnits(state, cmd.player, cmd.unitIds)) {
        u.order = { kind: 'attackBase', baseId: cmd.baseId };
        const base = baseById(state, cmd.baseId);
        if (base) orderMove(state, map, u, base.wx, base.wy, true);
      }
      break;

    case 'stop':
      for (const u of ownedUnits(state, cmd.player, cmd.unitIds)) {
        u.order = { kind: 'idle' };
        u.path = [];
      }
      break;

    case 'deploy':
      deployFromGarrison(state, map, cmd.baseId, cmd.unitDefId, cmd.count, cmd.player);
      break;

    case 'recall':
      recallToBase(state, cmd.player, cmd.unitIds);
      break;

    case 'build': {
      const base = ownedBase(state, cmd.player, cmd.baseId);
      if (!base) break;
      const result = startUpgrade(state, base, cmd.buildingId);
      if (!result.ok && player.isHuman) pushLog(state, result.reason, 'bad');
      break;
    }

    case 'queueUnit': {
      const base = ownedBase(state, cmd.player, cmd.baseId);
      if (!base) break;
      const result = queueUnit(state, base, cmd.unitDefId, cmd.count);
      if (!result.ok && player.isHuman) pushLog(state, result.reason, 'bad');
      break;
    }

    case 'cancelQueue': {
      const base = ownedBase(state, cmd.player, cmd.baseId);
      if (base) cancelQueueItem(state, base, cmd.index);
      break;
    }

    case 'research': {
      const result = startResearch(state, cmd.player, cmd.researchId);
      if (!result.ok && player.isHuman) pushLog(state, result.reason, 'bad');
      break;
    }

    case 'claim': {
      const territory = state.territories.find((t) => t.id === cmd.territoryId);
      if (!territory) break;
      for (const u of ownedUnits(state, cmd.player, cmd.unitIds)) {
        const def = UNIT_BY_ID[u.defId];
        if (!def?.specialisations.includes('apc')) continue;
        orderMove(state, map, u, territory.wx, territory.wy, true);
        u.order = { kind: 'claim', tx: territory.wx, ty: territory.wy, progress: 0 };
      }
      break;
    }

    case 'foundBase': {
      const result = foundBase(state, map, cmd.player, cmd.territoryId, cmd.kind as never);
      if (!result.ok && player.isHuman) pushLog(state, result.reason, 'bad');
      break;
    }
  }
}

function ownedUnits(state: GameState, player: PlayerId, ids: EntityId[]): Unit[] {
  const set = new Set(ids);
  return state.units.filter((u) => set.has(u.id) && u.owner === player);
}

function ownedBase(state: GameState, player: PlayerId, id: EntityId) {
  const base = baseById(state, id);
  return base && base.owner === player ? base : undefined;
}

/** Issues a path. `keepOrder` prevents an attack order being overwritten by idle. */
function orderMove(
  state: GameState,
  map: WorldMap,
  unit: Unit,
  x: number,
  y: number,
  keepOrder = false,
): void {
  const clamped = nearestTraversable(map, unit.defId, x, y);
  if (!clamped) return;
  unit.path = findPath(map, unit.defId, unit.x, unit.y, clamped.x, clamped.y);
  if (!keepOrder) unit.order = { kind: 'move', tx: clamped.x, ty: clamped.y };
  if (unit.path.length === 0 && state.players.find((p) => p.id === unit.owner)?.isHuman) {
    pushLog(state, `${UNIT_BY_ID[unit.defId]?.name ?? 'Unit'} cannot reach that position.`, 'bad');
  }
}

/** Spiral search outward for a tile this unit's domain can actually occupy. */
export function nearestTraversable(
  map: WorldMap,
  defId: string,
  x: number,
  y: number,
): { x: number; y: number } | null {
  if (canTraverse(map, defId, x, y)) return { x, y };
  for (let r = 1; r <= 24; r++) {
    for (let a = 0; a < r * 8; a++) {
      const angle = (a / (r * 8)) * Math.PI * 2;
      const nx = Math.round(x + Math.cos(angle) * r);
      const ny = Math.round(y + Math.sin(angle) * r);
      if (canTraverse(map, defId, nx, ny)) return { x: nx, y: ny };
    }
  }
  return null;
}

/** Moves units from a base's garrison onto the world map. */
export function deployFromGarrison(
  state: GameState,
  map: WorldMap,
  baseId: EntityId,
  unitDefId: string,
  count: number,
  player: PlayerId,
): void {
  const base = ownedBase(state, player, baseId);
  if (!base) return;
  const available = base.garrison[unitDefId] ?? 0;
  const n = Math.min(count, available);
  if (n <= 0) return;
  const def = UNIT_BY_ID[unitDefId];
  if (!def) return;

  let spawned = 0;
  for (let i = 0; i < n; i++) {
    const angle = (i / Math.max(1, n)) * Math.PI * 2;
    const radius = 3 + (i % 3);
    const sx = base.wx + Math.cos(angle) * radius;
    const sy = base.wy + Math.sin(angle) * radius;
    const spot = nearestTraversable(map, unitDefId, Math.round(sx), Math.round(sy));
    if (!spot) continue;
    const maxFuel = fuelCapacityFor(unitDefId);
    state.units.push({
      id: state.nextEntityId++,
      owner: player,
      defId: unitDefId,
      x: spot.x,
      y: spot.y,
      hp: def.hp,
      maxHp: def.hp,
      fuel: maxFuel,
      maxFuel,
      order: { kind: 'idle' },
      cooldown: 0,
      path: [],
      facing: angle,
      firedAt: -99,
    });
    spawned++;
  }

  if (spawned > 0) {
    base.garrison[unitDefId] = available - spawned;
    if (base.garrison[unitDefId] <= 0) delete base.garrison[unitDefId];
    recomputeBase(state, base);
  }
}

/** Returns deployed units to the nearest friendly base's garrison. */
function recallToBase(state: GameState, player: PlayerId, ids: EntityId[]): void {
  const units = ownedUnits(state, player, ids);
  const removed = new Set<EntityId>();
  for (const u of units) {
    const base = state.bases
      .filter((b) => b.owner === player)
      .sort((a, b) => Math.hypot(a.wx - u.x, a.wy - u.y) - Math.hypot(b.wx - u.x, b.wy - u.y))[0];
    if (!base) continue;
    if (Math.hypot(base.wx - u.x, base.wy - u.y) > 5) {
      u.order = { kind: 'garrison', baseId: base.id };
      continue;
    }
    base.garrison[u.defId] = (base.garrison[u.defId] ?? 0) + 1;
    removed.add(u.id);
    recomputeBase(state, base);
  }
  if (removed.size > 0) state.units = state.units.filter((u) => !removed.has(u.id));
}

/** Progresses claim orders; a held APC converts a territory after CLAIM_SECONDS. */
export function tickClaims(state: GameState, dt: number): void {
  for (const unit of state.units) {
    const order = unit.order;
    if (order.kind !== 'claim') continue;
    const territory = state.territories.find(
      (t) => Math.hypot(t.wx - order.tx, t.wy - order.ty) < 1,
    );
    if (!territory) {
      unit.order = { kind: 'idle' };
      continue;
    }
    const d = Math.hypot(territory.wx - unit.x, territory.wy - unit.y);
    if (d > CLAIM_RADIUS) continue;
    order.progress += dt;
    addEffect(state, {
      kind: 'claim', x: territory.wx, y: territory.wy, life: 0.1, scale: 1, colour: '#7fffa0',
    });
    if (order.progress >= 20) {
      territory.owner = unit.owner;
      unit.order = { kind: 'idle' };
      const p = playerById(state, unit.owner);
      if (p?.isHuman) pushLog(state, `${territory.name} captured.`, 'good');
    }
  }
}
