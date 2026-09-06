import type { GameState, Base, Player, PlayerId } from '../core/state';
import { pushLog, canAfford, spend, playerById } from '../core/state';
import type { WorldMap } from '../core/worldmap';
import { generateWorld, areaIsBuildable } from '../core/worldmap';
import { Rng } from '../core/rng';
import type { BaseKind } from '../data/types';
import { BASE_TYPE_BY_KIND, BASE_TYPES } from '../data/bases';
import { buildingHp, BUILDING_BY_ID } from '../data/buildings';
import { STARTING_RESOURCES, DEFAULT_SPEED } from '../data/config';
import { recomputeBase } from './economy';

/**
 * World and match setup: generating the map, placing players, and founding new bases.
 */

export interface MatchSetup {
  seed: number;
  playerName: string;
  /** Number of AI opponents. */
  enemies: number;
  /** Number of AI allies fighting alongside the human. */
  allies: number;
}

const AI_NAMES = [
  'Kharzan Directorate', 'Sahel Vanguard', 'Iron Meridian', 'Dune Syndicate',
  'Crimson Levant', 'Obsidian Pact', 'Azure Coalition', 'Ashfall Legion',
];

const COLOURS = [
  '#4da3ff', '#ff5c5c', '#57d977', '#ffb547',
  '#c479ff', '#38d6c6', '#ff7ad1', '#a8b4c0',
];

export interface Match {
  state: GameState;
  map: WorldMap;
}

export function createMatch(setup: MatchSetup): Match {
  const map = generateWorld(setup.seed);
  const rng = new Rng(setup.seed ^ 0x9e3779b9);

  const state: GameState = {
    tick: 0,
    time: 0,
    speed: DEFAULT_SPEED,
    rngState: rng.save(),
    players: [],
    bases: [],
    units: [],
    territories: [],
    nextEntityId: 1,
    effects: [],
    log: [],
    winner: null,
  };

  const total = 1 + setup.allies + setup.enemies;
  for (let i = 0; i < total; i++) {
    const isHuman = i === 0;
    const team = i <= setup.allies ? 0 : 1;
    state.players.push({
      id: i,
      name: isHuman ? setup.playerName : AI_NAMES[(i - 1) % AI_NAMES.length],
      colour: COLOURS[i % COLOURS.length],
      isHuman,
      team,
      resources: { ...STARTING_RESOURCES },
      research: {},
      researching: null,
      defeated: false,
    });
  }

  placeStartingBases(state, map, rng);
  createTerritories(state, map, rng);

  pushLog(state, 'Deployment complete. Establish your economy before expanding.', 'info');
  return { state, map };
}

/** Spreads starting Home bases as far apart as the site list allows. */
function placeStartingBases(state: GameState, map: WorldMap, rng: Rng): void {
  const sites = [...map.sites];
  if (sites.length === 0) return;
  const chosen: Array<{ x: number; y: number }> = [];

  for (const player of state.players) {
    let best = sites[0];
    let bestScore = -Infinity;
    for (const s of sites) {
      const minDist = chosen.length === 0
        ? 1000
        : Math.min(...chosen.map((c) => Math.hypot(c.x - s.x, c.y - s.y)));
      const score = minDist + rng.range(0, 6);
      if (score > bestScore) {
        bestScore = score;
        best = s;
      }
    }
    chosen.push(best);
    sites.splice(sites.indexOf(best), 1);
    state.bases.push(createBase(state, player.id, 'home', best.x, best.y, `${player.name} HQ`));
  }
}

/** Builds a base with its starting structures already in place. */
export function createBase(
  state: GameState,
  owner: PlayerId,
  kind: BaseKind,
  wx: number,
  wy: number,
  name: string,
): Base {
  const base: Base = {
    id: state.nextEntityId++,
    owner,
    kind,
    name,
    wx,
    wy,
    buildings: [],
    queue: [],
    garrison: {},
    cache: {
      income: { steel: 0, aluminum: 0, fuel: 0 },
      storage: { steel: 0, aluminum: 0, fuel: 0 },
      powerSupply: 0,
      powerDraw: 0,
      slotsUsed: 0,
      slotCapacity: 0,
      queueSlots: 1,
      commandLevel: 0,
      buildTimeMultiplier: 1,
      hasRadar: false,
      radarRange: 0,
    },
  };

  // Every base starts with a level-1 command post, a power plant and one of each mine,
  // matching Desert Order's "you always have something to upgrade" opening.
  const starters: Array<[string, number]> = [
    ['command_base', 3],
    ['power_plant', 2],
    ['steel_mine', 2],
    ['aluminum_mine', 1],
    ['fuel_pump', 1],
    ['military_central', 1],
    ['factory', 1],
  ];

  let cursorX = 0;
  let cursorY = 0;
  let rowHeight = 0;
  const grid = BASE_TYPE_BY_KIND[kind].grid;

  for (const [defId, level] of starters) {
    const def = BUILDING_BY_ID[defId];
    if (!def || !def.allowedBases.includes(kind)) continue;
    if (cursorX + def.size.w > grid.w) {
      cursorX = 0;
      cursorY += rowHeight;
      rowHeight = 0;
    }
    if (cursorY + def.size.h > grid.h) break;
    base.buildings.push({
      defId,
      level,
      x: cursorX,
      y: cursorY,
      hp: buildingHp(def, level),
      upgrading: null,
      disabled: false,
    });
    cursorX += def.size.w;
    rowHeight = Math.max(rowHeight, def.size.h);
  }

  recomputeBase(state, base);
  return base;
}

/** Scatters capturable resource territories over the remaining map sites. */
function createTerritories(state: GameState, map: WorldMap, rng: Rng): void {
  const taken = state.bases.map((b) => ({ x: b.wx, y: b.wy }));
  const names = ['Wadi', 'Ridge', 'Basin', 'Crossing', 'Depot', 'Outpost', 'Quarry', 'Well', 'Junction', 'Salt Flat'];

  for (const site of map.sites) {
    if (taken.some((t) => Math.hypot(t.x - site.x, t.y - site.y) < 18)) continue;
    const richness = rng.range(0.6, 1.8);
    const allows: BaseKind[] = site.coastal
      ? ['harbor', 'tank', 'air', 'helicopter', 'train']
      : ['tank', 'air', 'helicopter', 'train'];
    state.territories.push({
      id: state.nextEntityId++,
      wx: site.x,
      wy: site.y,
      radius: 6,
      owner: null,
      allows,
      name: `${rng.pick(names)} ${rng.int(11, 99)}`,
      bonus: {
        steel: 18 * richness,
        aluminum: 11 * richness,
        fuel: 5 * richness,
      },
    });
  }
}

export type FoundResult = { ok: true; base: Base } | { ok: false; reason: string };

/** Founds a new specialised base on a territory the player already controls. */
export function foundBase(
  state: GameState,
  map: WorldMap,
  playerId: PlayerId,
  territoryId: number,
  kind: BaseKind,
): FoundResult {
  const player = playerById(state, playerId);
  const territory = state.territories.find((t) => t.id === territoryId);
  if (!player || !territory) return { ok: false, reason: 'No such territory.' };
  if (territory.owner !== playerId) return { ok: false, reason: 'You do not control that territory.' };
  if (state.bases.some((b) => Math.hypot(b.wx - territory.wx, b.wy - territory.wy) < 10)) {
    return { ok: false, reason: 'A base already stands here.' };
  }
  const typeDef = BASE_TYPE_BY_KIND[kind];
  if (!typeDef) return { ok: false, reason: 'Unknown base type.' };
  if (!territory.allows.includes(kind)) {
    return { ok: false, reason: `A ${typeDef.name} cannot be founded here.` };
  }
  if (!areaIsBuildable(map, territory.wx, territory.wy, 5)) {
    return { ok: false, reason: 'The ground here is unsuitable.' };
  }
  if (!canAfford(player.resources, typeDef.foundingCost)) {
    return { ok: false, reason: 'Not enough resources to found this base.' };
  }
  spend(player.resources, typeDef.foundingCost);

  const base = createBase(state, playerId, kind, territory.wx, territory.wy, `${typeDef.name} ${territory.name}`);
  state.bases.push(base);
  if (player.isHuman) pushLog(state, `${typeDef.name} established at ${territory.name}.`, 'good');
  return { ok: true, base };
}

/** A player is defeated once every base they own has been levelled. */
export function checkDefeat(state: GameState): void {
  for (const player of state.players) {
    if (player.defeated) continue;
    const bases = state.bases.filter((b) => b.owner === player.id);
    const alive = bases.some((b) => b.buildings.some((x) => x.level > 0 && !x.disabled));
    if (bases.length > 0 && !alive) {
      player.defeated = true;
      pushLog(state, `${player.name} has been eliminated.`, player.isHuman ? 'bad' : 'good');
    }
  }

  if (state.winner !== null) return;
  const activeTeams = new Set(state.players.filter((p) => !p.defeated).map((p) => p.team));
  if (activeTeams.size === 1) {
    state.winner = [...activeTeams][0];
    const human = state.players.find((p) => p.isHuman);
    pushLog(
      state,
      human && human.team === state.winner ? 'Victory. The theatre is yours.' : 'Defeat. Your forces are broken.',
      human && human.team === state.winner ? 'good' : 'bad',
    );
  }
}

export function availableBaseKinds(): typeof BASE_TYPES {
  return BASE_TYPES.filter((b) => b.kind !== 'home');
}

export function playerBases(state: GameState, playerId: PlayerId): Base[] {
  return state.bases.filter((b) => b.owner === playerId);
}

export function humanPlayer(state: GameState): Player {
  return state.players.find((p) => p.isHuman) ?? state.players[0];
}
