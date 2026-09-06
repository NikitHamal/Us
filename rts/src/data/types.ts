/**
 * Core type vocabulary for the whole simulation.
 *
 * Kept dependency-free and serialisable on purpose: every value here must survive
 * JSON.stringify so the same structures can move over a wire to an authoritative
 * server later without a rewrite.
 */

/** The three resources the economy runs on, mirroring Desert Order's steel/aluminium/fuel. */
export type ResourceKind = 'steel' | 'aluminum' | 'fuel';

export type Resources = Record<ResourceKind, number>;

/**
 * Movement domain. This is the backbone of the Desert Order feel: a unit can only
 * exist on terrain matching its domain, and bases are specialised by domain.
 */
export type Domain = 'ground' | 'air' | 'naval' | 'rail';

/**
 * Specialisations from the reference table. A unit with a specialisation deals 5x
 * damage to matching targets and 1/5 damage to everything else; anti-base units are
 * an extreme case at 50x weaker against non-base targets.
 */
export type Specialisation =
  | 'none'
  | 'vs_vehicles'
  | 'vs_air'
  | 'vs_bases'
  | 'vs_boats'
  | 'vs_trains'
  | 'vs_copters'
  | 'vs_aircraft'
  | 'apc'
  | 'stealth'
  | 'detector'
  | 'support';

/** Base archetypes, one per screenshot in the reference sheet. */
export type BaseKind =
  | 'home'
  | 'tank'
  | 'helicopter'
  | 'harbor'
  | 'air'
  | 'train';

export interface UnitDef {
  id: string;
  name: string;
  domain: Domain;
  /** Build cost. Fuel is consumed while moving, not at build time. */
  cost: { steel: number; aluminum: number };
  /** Base damage per attack, before specialisation multipliers. */
  damage: number;
  armor: number;
  /** Attack range in tiles. */
  range: number;
  /** Fuel burned per second while moving. */
  fuel: number;
  /** Tiles per second. */
  speed: number;
  hp: number;
  specialisations: Specialisation[];
  /** Multiplies range when the unit's "2x/3x range engage" ability applies. */
  rangeEngageMultiplier?: number;
  /** Which base type can produce this unit. */
  producedAt: BaseKind;
  /** Seconds to build at speed multiplier 1. */
  buildTime: number;
  /** Population/slot cost against base capacity. */
  slots: number;
  description: string;
}

export interface BuildingDef {
  id: string;
  name: string;
  kind: BuildingKind;
  /** Cost at level 1. Subsequent levels scale by COST_GROWTH. */
  baseCost: Resources;
  baseBuildTime: number;
  maxLevel: number;
  /** Human-readable summary of what levelling this up actually does. */
  effect: string;
  /** Which base archetypes may host this building. */
  allowedBases: BaseKind[];
  /** Footprint on the base grid, in cells. */
  size: { w: number; h: number };
}

export type BuildingKind =
  | 'command'
  | 'power'
  | 'military'
  | 'steel_mine'
  | 'aluminum_mine'
  | 'fuel_pump'
  | 'steel_depot'
  | 'aluminum_depot'
  | 'fuel_depot'
  | 'factory'
  | 'research'
  | 'radar'
  | 'defense_tower'
  | 'wall'
  | 'repair_bay';

export interface ResearchDef {
  id: string;
  name: string;
  category: 'weapons' | 'armor' | 'economy' | 'speed' | 'logistics';
  maxLevel: number;
  baseCost: Resources;
  baseTime: number;
  /** Additive fraction per level, e.g. 0.05 = +5% per level. */
  perLevel: number;
  description: string;
}
