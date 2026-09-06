import type { BuildingDef, BaseKind, Resources } from './types';

/**
 * Building catalogue and the upgrade cost/effect curves.
 *
 * Desert Order's defining economic loop is that every structure is levelled
 * individually, each level costs geometrically more, and output scales slightly
 * slower than cost -- which is what forces expansion instead of turtling on one base.
 */

/** Cost of level N is baseCost * COST_GROWTH^(N-1). */
export const COST_GROWTH = 1.62;
/** Output of level N is baseOutput * OUTPUT_GROWTH^(N-1). */
export const OUTPUT_GROWTH = 1.68;
/** Build time of level N grows more slowly than cost, so late levels are cost-gated. */
export const TIME_GROWTH = 1.38;

const ALL_BASES: BaseKind[] = ['home', 'tank', 'helicopter', 'harbor', 'air', 'train'];

function res(steel: number, aluminum: number, fuel: number): Resources {
  return { steel, aluminum, fuel };
}

export const BUILDINGS: BuildingDef[] = [
  {
    id: 'command_base',
    name: 'Command Base',
    kind: 'command',
    baseCost: res(35000, 12000, 0),
    baseBuildTime: 60,
    maxLevel: 30,
    effect: 'Base headquarters. Its level caps the level of every other structure here and adds unit slots.',
    allowedBases: ALL_BASES,
    size: { w: 3, h: 3 },
  },
  {
    id: 'power_plant',
    name: 'Power Plant',
    kind: 'power',
    baseCost: res(20000, 4000, 0),
    baseBuildTime: 40,
    maxLevel: 30,
    effect: 'Supplies power. Mines and factories run at reduced rate when power demand exceeds supply.',
    allowedBases: ALL_BASES,
    size: { w: 2, h: 2 },
  },
  {
    id: 'military_central',
    name: 'Military Central',
    kind: 'military',
    baseCost: res(60000, 20000, 0),
    baseBuildTime: 90,
    maxLevel: 30,
    effect: 'Unlocks higher-tier units and adds +6 unit slots per level.',
    allowedBases: ALL_BASES,
    size: { w: 3, h: 2 },
  },
  {
    id: 'steel_mine',
    name: 'Steel Mine',
    kind: 'steel_mine',
    baseCost: res(12000, 2000, 0),
    baseBuildTime: 30,
    maxLevel: 30,
    effect: 'Produces steel every second.',
    allowedBases: ALL_BASES,
    size: { w: 2, h: 2 },
  },
  {
    id: 'aluminum_mine',
    name: 'Aluminium Refinery',
    kind: 'aluminum_mine',
    baseCost: res(16000, 3000, 0),
    baseBuildTime: 35,
    maxLevel: 30,
    effect: 'Produces aluminium every second.',
    allowedBases: ALL_BASES,
    size: { w: 2, h: 2 },
  },
  {
    id: 'fuel_pump',
    name: 'Fuel Derrick',
    kind: 'fuel_pump',
    baseCost: res(25000, 8000, 0),
    baseBuildTime: 45,
    maxLevel: 30,
    effect: 'Extracts fuel. Fuel is burned by moving units, not by construction.',
    allowedBases: ALL_BASES,
    size: { w: 2, h: 2 },
  },
  {
    id: 'steel_depot',
    name: 'Steel Depot',
    kind: 'steel_depot',
    baseCost: res(10000, 2500, 0),
    baseBuildTime: 30,
    maxLevel: 30,
    effect: 'Raises the steel storage ceiling. Production is wasted once storage is full.',
    allowedBases: ALL_BASES,
    size: { w: 2, h: 2 },
  },
  {
    id: 'aluminum_depot',
    name: 'Aluminium Depot',
    kind: 'aluminum_depot',
    baseCost: res(12000, 3000, 0),
    baseBuildTime: 30,
    maxLevel: 30,
    effect: 'Raises the aluminium storage ceiling.',
    allowedBases: ALL_BASES,
    size: { w: 2, h: 2 },
  },
  {
    id: 'fuel_depot',
    name: 'Fuel Depot',
    kind: 'fuel_depot',
    baseCost: res(18000, 6000, 0),
    baseBuildTime: 35,
    maxLevel: 30,
    effect: 'Raises the fuel storage ceiling.',
    allowedBases: ALL_BASES,
    size: { w: 2, h: 2 },
  },
  {
    id: 'factory',
    name: 'Assembly Factory',
    kind: 'factory',
    baseCost: res(45000, 15000, 0),
    baseBuildTime: 75,
    maxLevel: 30,
    effect: 'Each level cuts unit build time and adds a parallel production queue slot every 5 levels.',
    allowedBases: ALL_BASES,
    size: { w: 3, h: 3 },
  },
  {
    id: 'research_lab',
    name: 'Research Lab',
    kind: 'research',
    baseCost: res(80000, 40000, 0),
    baseBuildTime: 120,
    maxLevel: 30,
    effect: 'Unlocks research and speeds it up. Research applies army-wide across all your bases.',
    allowedBases: ['home'],
    size: { w: 3, h: 2 },
  },
  {
    id: 'radar',
    name: 'Radar Station',
    kind: 'radar',
    baseCost: res(35000, 25000, 0),
    baseBuildTime: 60,
    maxLevel: 20,
    effect: 'Extends map awareness and detects stealth units within its radius.',
    allowedBases: ALL_BASES,
    size: { w: 2, h: 2 },
  },
  {
    id: 'defense_tower',
    name: 'Defence Turret',
    kind: 'defense_tower',
    baseCost: res(30000, 10000, 0),
    baseBuildTime: 50,
    maxLevel: 25,
    effect: 'Automatically fires on hostile units in range. Damage and range scale per level.',
    allowedBases: ALL_BASES,
    size: { w: 1, h: 1 },
  },
  {
    id: 'wall',
    name: 'Perimeter Wall',
    kind: 'wall',
    baseCost: res(8000, 1000, 0),
    baseBuildTime: 20,
    maxLevel: 25,
    effect: 'Adds structural hit points to every building in this base.',
    allowedBases: ALL_BASES,
    size: { w: 1, h: 1 },
  },
  {
    id: 'repair_bay',
    name: 'Repair Bay',
    kind: 'repair_bay',
    baseCost: res(40000, 20000, 0),
    baseBuildTime: 70,
    maxLevel: 20,
    effect: 'Repairs friendly units stationed in this base, and refuels them faster.',
    allowedBases: ALL_BASES,
    size: { w: 2, h: 2 },
  },
];

export const BUILDING_BY_ID: Record<string, BuildingDef> = Object.fromEntries(
  BUILDINGS.map((b) => [b.id, b]),
);

/** Geometric cost curve, matching Desert Order's escalating upgrade prices. */
export function upgradeCost(def: BuildingDef, targetLevel: number): Resources {
  const f = Math.pow(COST_GROWTH, targetLevel - 1);
  return {
    steel: Math.round(def.baseCost.steel * f),
    aluminum: Math.round(def.baseCost.aluminum * f),
    fuel: Math.round(def.baseCost.fuel * f),
  };
}

export function upgradeTime(def: BuildingDef, targetLevel: number): number {
  return def.baseBuildTime * Math.pow(TIME_GROWTH, targetLevel - 1);
}

/** Per-second resource output for a mine at a given level. */
export const BASE_OUTPUT: Partial<Record<BuildingDef['kind'], number>> = {
  steel_mine: 180,
  aluminum_mine: 95,
  fuel_pump: 42,
};

export function output(kind: BuildingDef['kind'], level: number): number {
  const base = BASE_OUTPUT[kind];
  if (!base || level < 1) return 0;
  return base * Math.pow(OUTPUT_GROWTH, level - 1);
}

/** Storage ceiling contributed by one depot level. */
export function storageCapacity(level: number): number {
  if (level < 1) return 0;
  return 900000 * Math.pow(OUTPUT_GROWTH, level - 1);
}

/** Power produced / consumed. */
export function powerOutput(level: number): number {
  return level < 1 ? 0 : 50 * Math.pow(OUTPUT_GROWTH, level - 1);
}

export function powerDraw(def: BuildingDef, level: number): number {
  if (level < 1) return 0;
  const heavy: BuildingDef['kind'][] = ['steel_mine', 'aluminum_mine', 'fuel_pump', 'factory', 'radar', 'research'];
  if (!heavy.includes(def.kind)) return 0;
  return 18 * Math.pow(1.4, level - 1);
}

/** Defence turret combat stats by level. */
export function turretStats(level: number): { damage: number; range: number; hp: number } {
  return {
    damage: 400 * Math.pow(1.62, level - 1),
    range: 7 + Math.floor(level / 4),
    hp: 3000 * Math.pow(1.5, level - 1),
  };
}

/** Structural hit points of a building, before wall bonuses. */
export function buildingHp(def: BuildingDef, level: number): number {
  const footprint = def.size.w * def.size.h;
  return Math.round(1200 * footprint * Math.pow(1.45, level - 1));
}

/** Walls add a flat percentage to every structure's HP in the base. */
export function wallBonus(wallLevel: number): number {
  return wallLevel < 1 ? 0 : 0.15 * wallLevel;
}

/** Unit slot capacity granted by command base and military central levels. */
export function slotCapacity(commandLevel: number, militaryLevel: number): number {
  return 10 + commandLevel * 4 + militaryLevel * 6;
}

/** Factory speeds production; returns a multiplier applied to unit build time. */
export function factoryTimeMultiplier(level: number): number {
  return level < 1 ? 1 : Math.max(0.2, Math.pow(0.94, level));
}

export function factoryQueueSlots(level: number): number {
  return 1 + Math.floor(level / 5);
}

export function buildingsForBase(kind: BaseKind): BuildingDef[] {
  return BUILDINGS.filter((b) => b.allowedBases.includes(kind));
}
