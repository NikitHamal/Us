import type { BaseKind, UnitDef } from './types';
import { unitsForBase } from './units';

/**
 * Base archetypes, one per screenshot in the reference sheet.
 *
 * A player starts with a Home base and captures or constructs the specialised bases;
 * each specialised base is the only place its domain's units can be produced, which is
 * what makes territory control matter in Desert Order rather than fog-of-war scouting.
 */

export interface BaseTypeDef {
  kind: BaseKind;
  name: string;
  /** Base grid dimensions in cells. Home bases are the largest. */
  grid: { w: number; h: number };
  /** Which units this base can produce. */
  domainLabel: string;
  /** Terrain the base must be founded on. */
  terrain: 'land' | 'coast';
  /** Resource multiplier applied to this base's own extraction buildings. */
  economyMultiplier: number;
  description: string;
  /** Cost in resources to found a new base of this type on claimed territory. */
  foundingCost: { steel: number; aluminum: number; fuel: number };
  /** Accent colour used by the renderer for this base type. */
  accent: string;
}

export const BASE_TYPES: BaseTypeDef[] = [
  {
    kind: 'home',
    name: 'Home Base',
    grid: { w: 12, h: 12 },
    domainLabel: 'Command & economy',
    terrain: 'land',
    economyMultiplier: 1.25,
    description:
      'Your capital. The only base that can host a Research Lab, and the largest build grid. Losing it is not fatal, but it cripples research.',
    foundingCost: { steel: 0, aluminum: 0, fuel: 0 },
    accent: '#d9a441',
  },
  {
    kind: 'tank',
    name: 'Tank Base',
    grid: { w: 10, h: 10 },
    domainLabel: 'Ground vehicles',
    terrain: 'land',
    economyMultiplier: 1,
    description:
      'Heavy vehicle works. Produces every ground unit from the Conquest Truck up to the Karl-Geraet siege mortar.',
    foundingCost: { steel: 400000, aluminum: 150000, fuel: 20000 },
    accent: '#c2703a',
  },
  {
    kind: 'helicopter',
    name: 'Helicopter Base',
    grid: { w: 9, h: 9 },
    domainLabel: 'Rotorcraft',
    terrain: 'land',
    economyMultiplier: 0.9,
    description:
      'Rotor pads and hangars. Helicopters reach anywhere on the map quickly and ignore terrain entirely.',
    foundingCost: { steel: 350000, aluminum: 300000, fuel: 25000 },
    accent: '#6f8f4a',
  },
  {
    kind: 'harbor',
    name: 'Harbour Base',
    grid: { w: 11, h: 9 },
    domainLabel: 'Naval vessels',
    terrain: 'coast',
    economyMultiplier: 1.1,
    description:
      'Must be founded on a coastline. Docks produce everything from patrol launches to the Sumner destroyer.',
    foundingCost: { steel: 500000, aluminum: 250000, fuel: 30000 },
    accent: '#3f7f96',
  },
  {
    kind: 'air',
    name: 'Air Base',
    grid: { w: 12, h: 10 },
    domainLabel: 'Fixed-wing aircraft',
    terrain: 'land',
    economyMultiplier: 0.85,
    description:
      'Runways and revetments. Fixed-wing aircraft are the fastest units in the game but burn fuel ferociously.',
    foundingCost: { steel: 450000, aluminum: 450000, fuel: 40000 },
    accent: '#8a7fa8',
  },
  {
    kind: 'train',
    name: 'Train Base',
    grid: { w: 11, h: 8 },
    domainLabel: 'Rail artillery',
    terrain: 'land',
    economyMultiplier: 1.15,
    description:
      'Marshalling yard. Rail units are confined to track but carry the longest-ranged guns in the game.',
    foundingCost: { steel: 600000, aluminum: 200000, fuel: 15000 },
    accent: '#8c5a5a',
  },
];

export const BASE_TYPE_BY_KIND: Record<BaseKind, BaseTypeDef> = Object.fromEntries(
  BASE_TYPES.map((b) => [b.kind, b]),
) as Record<BaseKind, BaseTypeDef>;

export function producibleUnits(kind: BaseKind): UnitDef[] {
  return unitsForBase(kind);
}
