import type { GameState, Base, BaseCache, Player } from '../core/state';
import { emptyResources } from '../core/state';
import { BUILDING_BY_ID, output, storageCapacity, powerOutput, powerDraw, slotCapacity, factoryTimeMultiplier, factoryQueueSlots } from '../data/buildings';
import { BASE_TYPE_BY_KIND } from '../data/bases';
import { researchBonus } from '../data/research';
import { BASE_STORAGE } from '../data/config';
import { UNIT_BY_ID } from '../data/units';

/**
 * Economy: recomputes each base's derived output and applies income per tick.
 *
 * Output is cached rather than recalculated every tick because a large game can have
 * dozens of bases with fifteen buildings each, and this runs 20 times a second.
 */

export function recomputeBase(state: GameState, base: Base): void {
  const owner = state.players.find((p) => p.id === base.owner);
  const extraction = owner ? researchBonus('extraction', owner.research.extraction ?? 0) : 0;
  const typeDef = BASE_TYPE_BY_KIND[base.kind];

  const cache: BaseCache = {
    income: emptyResources(),
    storage: { steel: BASE_STORAGE, aluminum: BASE_STORAGE, fuel: BASE_STORAGE },
    powerSupply: 0,
    powerDraw: 0,
    slotsUsed: 0,
    slotCapacity: 0,
    queueSlots: 1,
    commandLevel: 0,
    buildTimeMultiplier: 1,
    hasRadar: false,
    radarRange: 0,
  };

  let militaryLevel = 0;
  let factoryLevel = 0;

  for (const b of base.buildings) {
    if (b.disabled || b.level < 1) continue;
    const def = BUILDING_BY_ID[b.defId];
    if (!def) continue;
    cache.powerDraw += powerDraw(def, b.level);
    switch (def.kind) {
      case 'command':
        cache.commandLevel = b.level;
        break;
      case 'power':
        cache.powerSupply += powerOutput(b.level);
        break;
      case 'military':
        militaryLevel = Math.max(militaryLevel, b.level);
        break;
      case 'factory':
        factoryLevel = Math.max(factoryLevel, b.level);
        break;
      case 'steel_mine':
        cache.income.steel += output('steel_mine', b.level);
        break;
      case 'aluminum_mine':
        cache.income.aluminum += output('aluminum_mine', b.level);
        break;
      case 'fuel_pump':
        cache.income.fuel += output('fuel_pump', b.level);
        break;
      case 'steel_depot':
        cache.storage.steel += storageCapacity(b.level);
        break;
      case 'aluminum_depot':
        cache.storage.aluminum += storageCapacity(b.level);
        break;
      case 'fuel_depot':
        cache.storage.fuel += storageCapacity(b.level);
        break;
      case 'radar':
        cache.hasRadar = true;
        cache.radarRange = Math.max(cache.radarRange, 18 + b.level * 3);
        break;
      default:
        break;
    }
  }

  // Brownout: if demand exceeds supply, extraction scales down proportionally.
  const powerRatio = cache.powerDraw <= 0 ? 1 : Math.min(1, cache.powerSupply / cache.powerDraw);
  const mult = typeDef.economyMultiplier * (1 + extraction) * powerRatio;
  cache.income.steel *= mult;
  cache.income.aluminum *= mult;
  cache.income.fuel *= mult;

  cache.slotCapacity = slotCapacity(cache.commandLevel, militaryLevel);
  cache.slotsUsed = garrisonSlots(base);
  cache.queueSlots = factoryQueueSlots(factoryLevel);
  const logistics = owner ? researchBonus('logistics', owner.research.logistics ?? 0) : 0;
  cache.buildTimeMultiplier = factoryTimeMultiplier(factoryLevel) * Math.max(0.15, 1 - logistics);

  base.cache = cache;
}

export function garrisonSlots(base: Base): number {
  let used = 0;
  for (const [defId, count] of Object.entries(base.garrison)) {
    const def = UNIT_BY_ID[defId];
    if (def) used += def.slots * count;
  }
  return used;
}

/** Total slots a player is using across garrisons and deployed units. */
export function playerSlots(state: GameState, playerId: number): { used: number; cap: number } {
  let used = 0;
  let cap = 0;
  for (const base of state.bases) {
    if (base.owner !== playerId) continue;
    cap += base.cache.slotCapacity;
    used += base.cache.slotsUsed;
  }
  for (const u of state.units) {
    if (u.owner !== playerId) continue;
    const def = UNIT_BY_ID[u.defId];
    if (def) used += def.slots;
  }
  return { used, cap };
}

/** Applies one tick of income to every player, clamped to storage. */
export function tickEconomy(state: GameState, dt: number): void {
  const capBy = new Map<number, { steel: number; aluminum: number; fuel: number }>();
  for (const p of state.players) {
    capBy.set(p.id, { steel: BASE_STORAGE, aluminum: BASE_STORAGE, fuel: BASE_STORAGE });
  }

  for (const base of state.bases) {
    const player = state.players.find((p) => p.id === base.owner);
    if (!player || player.defeated) continue;
    player.resources.steel += base.cache.income.steel * dt;
    player.resources.aluminum += base.cache.income.aluminum * dt;
    player.resources.fuel += base.cache.income.fuel * dt;
    const cap = capBy.get(base.owner);
    if (cap) {
      cap.steel += base.cache.storage.steel;
      cap.aluminum += base.cache.storage.aluminum;
      cap.fuel += base.cache.storage.fuel;
    }
  }

  // Territory bonuses reward map control the way Desert Order's resource fields do.
  for (const t of state.territories) {
    if (t.owner === null) continue;
    const player = state.players.find((p) => p.id === t.owner);
    if (!player || player.defeated) continue;
    player.resources.steel += t.bonus.steel * dt;
    player.resources.aluminum += t.bonus.aluminum * dt;
    player.resources.fuel += t.bonus.fuel * dt;
  }

  for (const p of state.players) {
    const cap = capBy.get(p.id);
    if (!cap) continue;
    p.resources.steel = Math.min(p.resources.steel, cap.steel);
    p.resources.aluminum = Math.min(p.resources.aluminum, cap.aluminum);
    p.resources.fuel = Math.min(p.resources.fuel, cap.fuel);
  }
}

/** Aggregate income figure shown in the HUD. */
export function playerIncome(state: GameState, playerId: number) {
  const total = emptyResources();
  for (const base of state.bases) {
    if (base.owner !== playerId) continue;
    total.steel += base.cache.income.steel;
    total.aluminum += base.cache.income.aluminum;
    total.fuel += base.cache.income.fuel;
  }
  for (const t of state.territories) {
    if (t.owner !== playerId) continue;
    total.steel += t.bonus.steel;
    total.aluminum += t.bonus.aluminum;
    total.fuel += t.bonus.fuel;
  }
  return total;
}

export function playerStorage(state: GameState, player: Player) {
  const total = { steel: BASE_STORAGE, aluminum: BASE_STORAGE, fuel: BASE_STORAGE };
  for (const base of state.bases) {
    if (base.owner !== player.id) continue;
    total.steel += base.cache.storage.steel;
    total.aluminum += base.cache.storage.aluminum;
    total.fuel += base.cache.storage.fuel;
  }
  return total;
}
