import type { UnitDef, Specialisation, Domain } from './types';

/**
 * Desert Order's damage model, implemented from the rules printed on the stat sheet:
 *
 *   "When a unit has special abilities, then its weapons are 5X stronger against this
 *    specialization and 5X weaker against everything else.
 *    (Units good against bases 50x weaker)"
 *
 * This single rule is what makes the game a counter-play game rather than a
 * biggest-number game, so it is modelled exactly rather than approximated.
 */

export const SPEC_BONUS = 5;
export const SPEC_PENALTY = 1 / 5;
/** Anti-base units are near-useless against anything that moves. */
export const ANTI_BASE_PENALTY = 1 / 50;

/** Categories a target can belong to for specialisation matching. */
export interface TargetProfile {
  domain: Domain;
  isBase: boolean;
  /** True for helicopters specifically, which are distinct from fixed-wing aircraft. */
  isCopter: boolean;
  isStealth: boolean;
}

export function profileOf(def: UnitDef): TargetProfile {
  return {
    domain: def.domain,
    isBase: false,
    isCopter: def.producedAt === 'helicopter',
    isStealth: def.specialisations.includes('stealth'),
  };
}

export const BASE_PROFILE: TargetProfile = {
  domain: 'ground',
  isBase: true,
  isCopter: false,
  isStealth: false,
};

/** Does a single specialisation tag match the given target? */
function matches(spec: Specialisation, target: TargetProfile): boolean {
  switch (spec) {
    case 'vs_bases':
      return target.isBase;
    case 'vs_air':
      return target.domain === 'air';
    case 'vs_copters':
      return target.isCopter;
    case 'vs_aircraft':
      return target.domain === 'air' && !target.isCopter;
    case 'vs_boats':
      return target.domain === 'naval';
    case 'vs_trains':
      return target.domain === 'rail';
    case 'vs_vehicles':
      return target.domain === 'ground' && !target.isBase;
    default:
      return false;
  }
}

/** Specialisation tags that actually influence damage (as opposed to utility tags). */
const OFFENSIVE: Specialisation[] = [
  'vs_bases', 'vs_air', 'vs_copters', 'vs_aircraft', 'vs_boats', 'vs_trains', 'vs_vehicles',
];

export function offensiveSpecs(def: UnitDef): Specialisation[] {
  return def.specialisations.filter((s) => OFFENSIVE.includes(s));
}

/**
 * Returns the damage multiplier this attacker applies to this target.
 * Generalists (no offensive specialisation) always deal exactly their listed damage.
 */
export function damageMultiplier(attacker: UnitDef, target: TargetProfile): number {
  const specs = offensiveSpecs(attacker);
  if (specs.length === 0) return 1;
  if (specs.some((s) => matches(s, target))) return SPEC_BONUS;
  return specs.includes('vs_bases') ? ANTI_BASE_PENALTY : SPEC_PENALTY;
}

/**
 * Effective range. Units with a "2x/3x range engage" ability only get the bonus
 * against targets they are specialised for; generalists with a multiplier (rocket
 * artillery, for example) get it unconditionally.
 */
export function effectiveRange(attacker: UnitDef, target: TargetProfile): number {
  const mult = attacker.rangeEngageMultiplier;
  if (!mult) return attacker.range;
  const specs = offensiveSpecs(attacker);
  if (specs.length === 0) return attacker.range * mult;
  return specs.some((s) => matches(s, target)) ? attacker.range * mult : attacker.range;
}

/**
 * Armor reduces incoming damage with diminishing returns rather than flat subtraction,
 * so a Maus is extremely durable without being literally immune to light weapons.
 */
export function armorReduction(armor: number): number {
  return 1 - armor / (armor + 2500);
}

export interface DamageContext {
  /** Additive weapons-research bonus, e.g. 0.25 for +25%. */
  attackerWeaponBonus: number;
  /** Additive armor-research bonus on the defender. */
  defenderArmorBonus: number;
}

const NO_BONUS: DamageContext = { attackerWeaponBonus: 0, defenderArmorBonus: 0 };

/** Full damage resolution for one attack. */
export function resolveDamage(
  attacker: UnitDef,
  target: TargetProfile,
  targetArmor: number,
  ctx: DamageContext = NO_BONUS,
): number {
  const raw = attacker.damage * (1 + ctx.attackerWeaponBonus);
  const spec = damageMultiplier(attacker, target);
  const armor = targetArmor * (1 + ctx.defenderArmorBonus);
  return Math.max(1, raw * spec * armorReduction(armor));
}

/** Can this attacker engage this target at all? Submarines are the notable restriction. */
export function canEngage(attacker: UnitDef, target: TargetProfile): boolean {
  if (attacker.damage <= 0) return false;
  if (attacker.id === 'm_class_sub') return target.domain === 'naval';
  // Ground units without an anti-air specialisation cannot shoot at aircraft.
  if (target.domain === 'air' && attacker.domain === 'ground') {
    return attacker.specialisations.some(
      (s) => s === 'vs_air' || s === 'vs_aircraft' || s === 'vs_copters',
    );
  }
  return true;
}

/** Stealth units are only targetable when the attacking side fields a detector. */
export function isVisibleTo(target: TargetProfile, viewerHasDetector: boolean): boolean {
  return !target.isStealth || viewerHasDetector;
}
