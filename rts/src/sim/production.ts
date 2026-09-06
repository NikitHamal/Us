import type { GameState, Base } from '../core/state';
import { canAfford, spend, pushLog, playerById } from '../core/state';
import { BUILDING_BY_ID, upgradeCost, upgradeTime, buildingHp, wallBonus } from '../data/buildings';
import { UNIT_BY_ID, canProduceAt } from '../data/units';
import { RESEARCH_BY_ID, researchCost, researchTime, researchBonus } from '../data/research';
import { recomputeBase, playerSlots } from './economy';

/**
 * Construction, unit production and research progress.
 *
 * All three share the same shape -- pay up front, then burn down a timer -- which keeps
 * refund and cancellation behaviour consistent across the whole game.
 */

export type BuildResult = { ok: true } | { ok: false; reason: string };

/** Highest level any building may reach, gated by the Command Base. */
export function levelCap(base: Base, defId: string): number {
  const def = BUILDING_BY_ID[defId];
  if (!def) return 0;
  if (def.kind === 'command') return def.maxLevel;
  return Math.min(def.maxLevel, base.cache.commandLevel);
}

export function startUpgrade(state: GameState, base: Base, defId: string): BuildResult {
  const def = BUILDING_BY_ID[defId];
  if (!def) return { ok: false, reason: 'Unknown building.' };
  if (!def.allowedBases.includes(base.kind)) {
    return { ok: false, reason: `${def.name} cannot be built at a ${base.kind} base.` };
  }
  const player = playerById(state, base.owner);
  if (!player) return { ok: false, reason: 'No such player.' };

  let instance = base.buildings.find((b) => b.defId === defId);
  const currentLevel = instance?.level ?? 0;
  const target = currentLevel + 1;

  if (instance?.upgrading) return { ok: false, reason: 'Already under construction.' };
  if (target > levelCap(base, defId)) {
    return { ok: false, reason: 'Upgrade the Command Base first.' };
  }

  const cost = upgradeCost(def, target);
  if (!canAfford(player.resources, cost)) return { ok: false, reason: 'Not enough resources.' };
  spend(player.resources, cost);

  if (!instance) {
    const spot = findFreeCell(base, def.size.w, def.size.h);
    if (!spot) return { ok: false, reason: 'No space left in this base.' };
    instance = {
      defId,
      level: 0,
      x: spot.x,
      y: spot.y,
      hp: 1,
      upgrading: null,
      disabled: false,
    };
    base.buildings.push(instance);
  }
  instance.upgrading = { targetLevel: target, remaining: upgradeTime(def, target) };
  return { ok: true };
}

/** Simple first-fit placement on the base grid, respecting footprints. */
function findFreeCell(base: Base, w: number, h: number): { x: number; y: number } | null {
  const grid = baseGridSize(base);
  const occupied = (x: number, y: number) =>
    base.buildings.some((b) => {
      const d = BUILDING_BY_ID[b.defId];
      if (!d) return false;
      return x >= b.x && x < b.x + d.size.w && y >= b.y && y < b.y + d.size.h;
    });
  for (let y = 0; y + h <= grid.h; y++) {
    for (let x = 0; x + w <= grid.w; x++) {
      let free = true;
      for (let dy = 0; dy < h && free; dy++) {
        for (let dx = 0; dx < w && free; dx++) {
          if (occupied(x + dx, y + dy)) free = false;
        }
      }
      if (free) return { x, y };
    }
  }
  return null;
}

import { BASE_TYPE_BY_KIND } from '../data/bases';

export function baseGridSize(base: Base) {
  return BASE_TYPE_BY_KIND[base.kind].grid;
}

/** Advances construction timers. dt is already scaled by the speed multiplier. */
export function tickConstruction(state: GameState, dt: number): void {
  for (const base of state.bases) {
    let changed = false;
    for (const b of base.buildings) {
      if (!b.upgrading) continue;
      b.upgrading.remaining -= dt;
      if (b.upgrading.remaining <= 0) {
        b.level = b.upgrading.targetLevel;
        b.upgrading = null;
        const def = BUILDING_BY_ID[b.defId];
        if (def) {
          const wall = base.buildings.find((w) => w.defId === 'wall');
          b.hp = buildingHp(def, b.level) * (1 + wallBonus(wall?.level ?? 0));
        }
        changed = true;
      }
    }
    if (changed) recomputeBase(state, base);
  }
}

// ------------------------------------------------------------------ units

export function queueUnit(state: GameState, base: Base, unitDefId: string, count = 1): BuildResult {
  const def = UNIT_BY_ID[unitDefId];
  if (!def) return { ok: false, reason: 'Unknown unit.' };
  if (!canProduceAt(base.kind, def)) {
    return { ok: false, reason: `${def.name} is produced at a ${def.producedAt} base.` };
  }
  const player = playerById(state, base.owner);
  if (!player) return { ok: false, reason: 'No such player.' };

  const military = base.buildings.find((b) => b.defId === 'military_central');
  if (!military || military.level < 1) {
    return { ok: false, reason: 'Build a Military Central first.' };
  }
  if (!unitUnlocked(def.id, military.level)) {
    return { ok: false, reason: 'Military Central level too low for this unit.' };
  }

  const slots = playerSlots(state, player.id);
  if (slots.used + def.slots * count > slots.cap) {
    return { ok: false, reason: 'Not enough unit slots. Upgrade Military Central.' };
  }

  const cost = { steel: def.cost.steel * count, aluminum: def.cost.aluminum * count, fuel: 0 };
  if (!canAfford(player.resources, cost)) return { ok: false, reason: 'Not enough resources.' };
  spend(player.resources, cost);

  const total = def.buildTime * base.cache.buildTimeMultiplier;
  for (let i = 0; i < count; i++) {
    base.queue.push({ unitDefId, remaining: total, total });
  }
  return { ok: true };
}

/**
 * Higher-tier units require a bigger Military Central. Tiers are derived from cost so
 * the gate stays correct automatically as units are tuned.
 */
export function unitTier(unitDefId: string): number {
  const def = UNIT_BY_ID[unitDefId];
  if (!def) return 99;
  const total = def.cost.steel + def.cost.aluminum;
  if (total < 150_000) return 1;
  if (total < 800_000) return 2;
  if (total < 2_500_000) return 3;
  if (total < 8_000_000) return 4;
  if (total < 20_000_000) return 5;
  return 6;
}

export function unitUnlocked(unitDefId: string, militaryLevel: number): boolean {
  return militaryLevel >= requiredMilitaryLevel(unitDefId);
}

export function requiredMilitaryLevel(unitDefId: string): number {
  return (unitTier(unitDefId) - 1) * 3 + 1;
}

/** Advances production queues; completed units join the base garrison. */
export function tickProduction(state: GameState, dt: number): void {
  for (const base of state.bases) {
    if (base.queue.length === 0) continue;
    const parallel = Math.min(base.cache.queueSlots, base.queue.length);
    let completed = false;
    for (let i = 0; i < parallel; i++) {
      const item = base.queue[i];
      item.remaining -= dt;
      if (item.remaining <= 0) {
        base.garrison[item.unitDefId] = (base.garrison[item.unitDefId] ?? 0) + 1;
        completed = true;
      }
    }
    if (completed) {
      base.queue = base.queue.filter((q) => q.remaining > 0);
      recomputeBase(state, base);
    }
  }
}

export function cancelQueueItem(state: GameState, base: Base, index: number): void {
  const item = base.queue[index];
  if (!item) return;
  const def = UNIT_BY_ID[item.unitDefId];
  const player = playerById(state, base.owner);
  if (def && player) {
    // Refund proportional to remaining work, so cancelling late is costly.
    const refund = item.remaining / item.total;
    player.resources.steel += def.cost.steel * refund;
    player.resources.aluminum += def.cost.aluminum * refund;
  }
  base.queue.splice(index, 1);
}

// --------------------------------------------------------------- research

export function startResearch(state: GameState, playerId: number, researchId: string): BuildResult {
  const player = playerById(state, playerId);
  const def = RESEARCH_BY_ID[researchId];
  if (!player || !def) return { ok: false, reason: 'Unknown research.' };
  if (player.researching) return { ok: false, reason: 'Another project is already running.' };

  const hasLab = state.bases.some(
    (b) => b.owner === playerId && b.buildings.some((x) => x.defId === 'research_lab' && x.level > 0),
  );
  if (!hasLab) return { ok: false, reason: 'Build a Research Lab at your Home Base.' };

  const level = (player.research[researchId] ?? 0) + 1;
  if (level > def.maxLevel) return { ok: false, reason: 'Already at maximum level.' };

  const cost = researchCost(def, level);
  if (!canAfford(player.resources, cost)) return { ok: false, reason: 'Not enough resources.' };
  spend(player.resources, cost);

  const labLevel = Math.max(
    ...state.bases
      .filter((b) => b.owner === playerId)
      .flatMap((b) => b.buildings.filter((x) => x.defId === 'research_lab').map((x) => x.level)),
    1,
  );
  const time = researchTime(def, level) * Math.max(0.25, Math.pow(0.95, labLevel));
  player.researching = { id: researchId, targetLevel: level, remaining: time };
  return { ok: true };
}

export function tickResearch(state: GameState, dt: number): void {
  for (const player of state.players) {
    const job = player.researching;
    if (!job) continue;
    job.remaining -= dt;
    if (job.remaining <= 0) {
      player.research[job.id] = job.targetLevel;
      player.researching = null;
      const def = RESEARCH_BY_ID[job.id];
      if (player.isHuman && def) {
        pushLog(state, `${def.name} advanced to level ${job.targetLevel}.`, 'good');
      }
      for (const base of state.bases) {
        if (base.owner === player.id) recomputeBase(state, base);
      }
    }
  }
}

/** Convenience accessor used by combat and movement. */
export function bonusFor(state: GameState, playerId: number, id: string): number {
  const player = playerById(state, playerId);
  return player ? researchBonus(id, player.research[id] ?? 0) : 0;
}
