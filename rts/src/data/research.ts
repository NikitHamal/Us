import type { ResearchDef, Resources } from './types';

/**
 * Army-wide research. Unlike buildings, research is global to a player rather than
 * per-base, so it is the main long-term investment sink once bases are maxed.
 */

export const RESEARCH_COST_GROWTH = 2.05;
export const RESEARCH_TIME_GROWTH = 1.6;

function res(steel: number, aluminum: number, fuel: number): Resources {
  return { steel, aluminum, fuel };
}

export const RESEARCH: ResearchDef[] = [
  {
    id: 'weapons',
    name: 'Weapon Calibration',
    category: 'weapons',
    maxLevel: 25,
    baseCost: res(120000, 60000, 5000),
    baseTime: 180,
    perLevel: 0.05,
    description: '+5% damage per level for every unit you field.',
  },
  {
    id: 'armor',
    name: 'Composite Plating',
    category: 'armor',
    maxLevel: 25,
    baseCost: res(140000, 50000, 5000),
    baseTime: 190,
    perLevel: 0.05,
    description: '+5% effective armour per level for every unit you field.',
  },
  {
    id: 'targeting',
    name: 'Targeting Optics',
    category: 'weapons',
    maxLevel: 20,
    baseCost: res(160000, 90000, 8000),
    baseTime: 220,
    perLevel: 0.04,
    description: '+4% weapon range per level.',
  },
  {
    id: 'engines',
    name: 'Engine Tuning',
    category: 'speed',
    maxLevel: 20,
    baseCost: res(90000, 70000, 12000),
    baseTime: 160,
    perLevel: 0.06,
    description: '+6% movement speed per level.',
  },
  {
    id: 'fuel_efficiency',
    name: 'Fuel Efficiency',
    category: 'logistics',
    maxLevel: 20,
    baseCost: res(80000, 60000, 20000),
    baseTime: 170,
    perLevel: 0.04,
    description: '-4% fuel consumption per level while moving.',
  },
  {
    id: 'extraction',
    name: 'Extraction Techniques',
    category: 'economy',
    maxLevel: 25,
    baseCost: res(100000, 40000, 4000),
    baseTime: 150,
    perLevel: 0.05,
    description: '+5% output per level from every mine, refinery and derrick.',
  },
  {
    id: 'logistics',
    name: 'Logistics Network',
    category: 'logistics',
    maxLevel: 20,
    baseCost: res(110000, 80000, 10000),
    baseTime: 200,
    perLevel: 0.04,
    description: '-4% unit build time per level.',
  },
  {
    id: 'fortification',
    name: 'Fortification',
    category: 'armor',
    maxLevel: 20,
    baseCost: res(130000, 45000, 6000),
    baseTime: 210,
    perLevel: 0.06,
    description: '+6% structure hit points per level across all your bases.',
  },
];

export const RESEARCH_BY_ID: Record<string, ResearchDef> = Object.fromEntries(
  RESEARCH.map((r) => [r.id, r]),
);

export function researchCost(def: ResearchDef, targetLevel: number): Resources {
  const f = Math.pow(RESEARCH_COST_GROWTH, targetLevel - 1);
  return {
    steel: Math.round(def.baseCost.steel * f),
    aluminum: Math.round(def.baseCost.aluminum * f),
    fuel: Math.round(def.baseCost.fuel * f),
  };
}

export function researchTime(def: ResearchDef, targetLevel: number): number {
  return def.baseTime * Math.pow(RESEARCH_TIME_GROWTH, targetLevel - 1);
}

/** Total additive bonus from a research line at the given level. */
export function researchBonus(id: string, level: number): number {
  const def = RESEARCH_BY_ID[id];
  return def ? def.perLevel * level : 0;
}
