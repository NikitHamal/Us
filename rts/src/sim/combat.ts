import type { GameState, Unit, Base } from '../core/state';
import { addEffect, pushLog, isHostile, playerById } from '../core/state';
import { UNIT_BY_ID } from '../data/units';
import { BUILDING_BY_ID, turretStats, buildingHp, wallBonus } from '../data/buildings';
import {
  resolveDamage,
  effectiveRange,
  canEngage,
  profileOf,
  BASE_PROFILE,
} from '../data/combat';
import { FIRE_RATE } from '../data/config';
import { bonusFor } from './production';
import { recomputeBase } from './economy';

/**
 * Combat resolution.
 *
 * Targeting deliberately respects the specialisation table: a unit prefers targets it
 * is strong against, which is what makes mixed armies beat monolithic ones and is the
 * central skill expression in Desert Order.
 */

/**
 * Per-tick combat cache.
 *
 * Targeting is the hottest code in the simulation: naively, every attacker tests every
 * target, and each test re-scanned the unit list for detectors and the player list for
 * research bonuses. That is O(n^3). Everything invariant within a tick is computed once
 * here instead, which turns the inner loop into pure arithmetic.
 */
interface CombatCache {
  tick: number;
  detectors: Set<number>;
  weaponBonus: Map<number, number>;
  armorBonus: Map<number, number>;
  rangeBonus: Map<number, number>;
  teams: Map<number, number>;
}

let cache: CombatCache | null = null;

function combatCache(state: GameState): CombatCache {
  if (cache && cache.tick === state.tick) return cache;
  const detectors = new Set<number>();
  for (const u of state.units) {
    if (UNIT_BY_ID[u.defId]?.specialisations.includes('detector')) detectors.add(u.owner);
  }
  const weaponBonus = new Map<number, number>();
  const armorBonus = new Map<number, number>();
  const rangeBonus = new Map<number, number>();
  const teams = new Map<number, number>();
  for (const p of state.players) {
    weaponBonus.set(p.id, bonusFor(state, p.id, 'weapons'));
    armorBonus.set(p.id, bonusFor(state, p.id, 'armor'));
    rangeBonus.set(p.id, 1 + bonusFor(state, p.id, 'targeting'));
    teams.set(p.id, p.team);
  }
  cache = { tick: state.tick, detectors, weaponBonus, armorBonus, rangeBonus, teams };
  return cache;
}

/** Does this player field any detector unit, and can therefore see stealth? */
export function hasDetector(state: GameState, playerId: number): boolean {
  return combatCache(state).detectors.has(playerId);
}

/** Radar stations also reveal stealth within their radius. */
function radarCovers(state: GameState, playerId: number, x: number, y: number): boolean {
  return state.bases.some(
    (b) =>
      b.owner === playerId &&
      b.cache.hasRadar &&
      Math.hypot(b.wx - x, b.wy - y) <= b.cache.radarRange,
  );
}

export function canSee(state: GameState, viewer: number, target: Unit): boolean {
  const profile = profileOf(UNIT_BY_ID[target.defId]);
  if (!profile.isStealth) return true;
  return hasDetector(state, viewer) || radarCovers(state, viewer, target.x, target.y);
}

/**
 * Picks the best target in range: highest expected damage per shot, so specialised
 * units naturally seek out what they counter instead of shooting the nearest thing.
 */
function pickTarget(state: GameState, unit: Unit): Unit | Base | null {
  const def = UNIT_BY_ID[unit.defId];
  if (!def || def.damage <= 0) return null;
  const c = combatCache(state);
  const weapons = c.weaponBonus.get(unit.owner) ?? 0;
  const rangeBonus = c.rangeBonus.get(unit.owner) ?? 1;
  const myTeam = c.teams.get(unit.owner);
  const iDetect = c.detectors.has(unit.owner);

  // Widest reach this unit could possibly have, used to reject targets cheaply.
  const maxReach = def.range * (def.rangeEngageMultiplier ?? 1) * rangeBonus;
  const maxReachSq = maxReach * maxReach;

  let best: Unit | Base | null = null;
  let bestScore = 0;

  for (const other of state.units) {
    if (other.hp <= 0) continue;
    if (c.teams.get(other.owner) === myTeam) continue;
    const dx = other.x - unit.x;
    const dy = other.y - unit.y;
    const dsq = dx * dx + dy * dy;
    if (dsq > maxReachSq) continue;

    const otherDef = UNIT_BY_ID[other.defId];
    if (!otherDef) continue;
    const profile = profileOf(otherDef);
    if (!canEngage(def, profile)) continue;
    if (profile.isStealth && !iDetect && !radarCovers(state, unit.owner, other.x, other.y)) continue;

    const d = Math.sqrt(dsq);
    if (d > effectiveRange(def, profile) * rangeBonus) continue;

    const dmg = resolveDamage(def, profile, otherDef.armor, {
      attackerWeaponBonus: weapons,
      defenderArmorBonus: c.armorBonus.get(other.owner) ?? 0,
    });
    // Prefer targets we can actually finish, and break ties by proximity.
    const score = Math.min(dmg, other.hp) * (1 + 1 / (1 + d));
    if (score > bestScore) {
      bestScore = score;
      best = other;
    }
  }

  for (const base of state.bases) {
    if (c.teams.get(base.owner) === myTeam) continue;
    const dx = base.wx - unit.x;
    const dy = base.wy - unit.y;
    const dsq = dx * dx + dy * dy;
    if (dsq > maxReachSq) continue;
    const d = Math.sqrt(dsq);
    if (d > effectiveRange(def, BASE_PROFILE) * rangeBonus) continue;
    const dmg = resolveDamage(def, BASE_PROFILE, 0, {
      attackerWeaponBonus: weapons,
      defenderArmorBonus: 0,
    });
    const score = dmg * (1 + 1 / (1 + d));
    if (score > bestScore) {
      bestScore = score;
      best = base;
    }
  }

  return best;
}

function isBase(t: Unit | Base): t is Base {
  return (t as Base).buildings !== undefined;
}

/** Applies damage to the weakest surviving building in a base. */
function damageBase(state: GameState, base: Base, amount: number): void {
  const alive = base.buildings.filter((b) => b.level > 0 && !b.disabled && b.hp > 0);
  if (alive.length === 0) return;
  // Attack the most damaged structure so bases fall progressively rather than evenly.
  const target = alive.reduce((a, b) => (a.hp <= b.hp ? a : b));
  target.hp -= amount;
  if (target.hp <= 0) {
    target.hp = 0;
    target.disabled = true;
    const def = BUILDING_BY_ID[target.defId];
    const owner = playerById(state, base.owner);
    if (def && owner?.isHuman) {
      pushLog(state, `${def.name} destroyed at ${base.name}.`, 'bad');
    }
    recomputeBase(state, base);
  }
  addEffect(state, {
    kind: 'explosion',
    x: base.wx,
    y: base.wy,
    life: 0.4,
    scale: 1.3,
    colour: '#ff9a3c',
  });
}

/** One tick of unit weapon fire. */
export function tickCombat(state: GameState, dt: number): void {
  const dead: Unit[] = [];

  for (const unit of state.units) {
    if (unit.hp <= 0) continue;
    unit.cooldown -= dt;
    if (unit.cooldown > 0) continue;

    const def = UNIT_BY_ID[unit.defId];
    if (!def || def.damage <= 0) continue;

    const target = pickTarget(state, unit);
    if (!target) continue;

    unit.cooldown = 1 / FIRE_RATE;
    unit.firedAt = state.time;
    const weapons = combatCache(state).weaponBonus.get(unit.owner) ?? 0;

    if (isBase(target)) {
      const dmg = resolveDamage(def, BASE_PROFILE, 0, {
        attackerWeaponBonus: weapons,
        defenderArmorBonus: 0,
      });
      damageBase(state, target, dmg);
      addEffect(state, {
        kind: 'shot', x: unit.x, y: unit.y, tx: target.wx, ty: target.wy,
        life: 0.18, scale: 1, colour: '#ffd27a',
      });
    } else {
      const targetDef = UNIT_BY_ID[target.defId];
      if (!targetDef) continue;
      const dmg = resolveDamage(def, profileOf(targetDef), targetDef.armor, {
        attackerWeaponBonus: weapons,
        defenderArmorBonus: combatCache(state).armorBonus.get(target.owner) ?? 0,
      });
      target.hp -= dmg;
      addEffect(state, {
        kind: 'shot', x: unit.x, y: unit.y, tx: target.x, ty: target.y,
        life: 0.18, scale: 1, colour: '#ffd27a',
      });
      if (target.hp <= 0) dead.push(target);
    }
  }

  if (dead.length > 0) {
    for (const d of dead) {
      addEffect(state, { kind: 'explosion', x: d.x, y: d.y, life: 0.5, scale: 1, colour: '#ff7a33' });
    }
    const deadIds = new Set(dead.map((d) => d.id));
    state.units = state.units.filter((u) => !deadIds.has(u.id));
  }
}

/** Base defence turrets fire independently of any unit orders. */
export function tickTurrets(state: GameState, dt: number): void {
  for (const base of state.bases) {
    for (const b of base.buildings) {
      if (b.defId !== 'defense_tower' || b.level < 1 || b.disabled) continue;
      const stats = turretStats(b.level);
      // Reuse the building's hp field slot for cooldown bookkeeping via a side map.
      const cd = turretCooldowns.get(b) ?? 0;
      const next = cd - dt;
      if (next > 0) {
        turretCooldowns.set(b, next);
        continue;
      }
      const target = state.units.find(
        (u) =>
          u.hp > 0 &&
          isHostile(state, base.owner, u.owner) &&
          Math.hypot(u.x - base.wx, u.y - base.wy) <= stats.range &&
          canSee(state, base.owner, u),
      );
      if (!target) continue;
      turretCooldowns.set(b, 1 / FIRE_RATE);
      const targetDef = UNIT_BY_ID[target.defId];
      if (!targetDef) continue;
      const armorBonus = combatCache(state).armorBonus.get(target.owner) ?? 0;
      const reduction = 1 - (targetDef.armor * (1 + armorBonus)) / (targetDef.armor * (1 + armorBonus) + 2500);
      target.hp -= stats.damage * reduction;
      addEffect(state, {
        kind: 'shot', x: base.wx, y: base.wy, tx: target.x, ty: target.y,
        life: 0.15, scale: 1, colour: '#9fe0ff',
      });
      if (target.hp <= 0) {
        addEffect(state, { kind: 'explosion', x: target.x, y: target.y, life: 0.5, scale: 1, colour: '#ff7a33' });
        state.units = state.units.filter((u) => u.id !== target.id);
      }
    }
  }
}

/** Turret cooldowns live outside the serialised state; they are purely transient. */
const turretCooldowns = new WeakMap<object, number>();

/** Recomputes a building's max HP, used when walls are upgraded. */
export function refreshBuildingHp(base: Base): void {
  const wall = base.buildings.find((w) => w.defId === 'wall');
  const bonus = 1 + wallBonus(wall?.level ?? 0);
  for (const b of base.buildings) {
    const def = BUILDING_BY_ID[b.defId];
    if (!def || b.level < 1) continue;
    const max = buildingHp(def, b.level) * bonus;
    b.hp = Math.min(max, b.hp <= 0 ? max : b.hp);
  }
}
