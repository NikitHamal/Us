import type { BaseKind, Resources } from '../data/types';
import type { SpeedPreset } from '../data/config';

/**
 * The complete serialisable world state.
 *
 * Every field here is plain data. The simulation is a pure function of
 * (state, commands, dt), which is the property that lets the same code run
 * client-side today and server-authoritative later without restructuring.
 */

export type PlayerId = number;
export type EntityId = number;

export type Diplomacy = 'self' | 'ally' | 'enemy' | 'neutral';

export interface Player {
  id: PlayerId;
  name: string;
  colour: string;
  isHuman: boolean;
  /** Team number. Players sharing a team are allies. */
  team: number;
  resources: Resources;
  /** Research line id -> level. */
  research: Record<string, number>;
  /** Currently researching, if anything. */
  researching: { id: string; targetLevel: number; remaining: number } | null;
  defeated: boolean;
}

export interface BuildingInstance {
  /** Catalogue id from data/buildings.ts */
  defId: string;
  level: number;
  /** Grid position within the base. */
  x: number;
  y: number;
  hp: number;
  /** Non-null while a construction or upgrade is in progress. */
  upgrading: { targetLevel: number; remaining: number } | null;
  /** Disabled buildings produce nothing (destroyed or unpowered). */
  disabled: boolean;
}

export interface QueueItem {
  unitDefId: string;
  remaining: number;
  total: number;
}

export interface Base {
  id: EntityId;
  owner: PlayerId;
  kind: BaseKind;
  name: string;
  /** World tile position of the base centre. */
  wx: number;
  wy: number;
  buildings: BuildingInstance[];
  queue: QueueItem[];
  /** Units currently garrisoned here rather than deployed on the map. */
  garrison: Record<string, number>;
  /** Cached derived values, recomputed when buildings change. */
  cache: BaseCache;
}

export interface BaseCache {
  income: Resources;
  storage: Resources;
  powerSupply: number;
  powerDraw: number;
  slotsUsed: number;
  slotCapacity: number;
  queueSlots: number;
  commandLevel: number;
  buildTimeMultiplier: number;
  hasRadar: boolean;
  radarRange: number;
}

export type UnitOrder =
  | { kind: 'idle' }
  | { kind: 'move'; tx: number; ty: number }
  | { kind: 'attack'; targetId: EntityId }
  | { kind: 'attackBase'; baseId: EntityId }
  | { kind: 'claim'; tx: number; ty: number; progress: number }
  | { kind: 'garrison'; baseId: EntityId };

export interface Unit {
  id: EntityId;
  owner: PlayerId;
  defId: string;
  /** Continuous world position in tiles. */
  x: number;
  y: number;
  hp: number;
  maxHp: number;
  fuel: number;
  maxFuel: number;
  order: UnitOrder;
  /** Seconds until this unit may fire again. */
  cooldown: number;
  /** Path of waypoints currently being followed. */
  path: Array<{ x: number; y: number }>;
  /** Facing in radians, for rendering. */
  facing: number;
  /** Set when the unit fired this tick, so the renderer can draw a muzzle flash. */
  firedAt: number;
}

/** A capturable point of interest on the world map. */
export interface Territory {
  id: EntityId;
  wx: number;
  wy: number;
  radius: number;
  owner: PlayerId | null;
  /** Site where a base of this type may be founded, if any. */
  allows: BaseKind[];
  name: string;
  /** Passive resource bonus granted to the owner, per second. */
  bonus: Resources;
}

export interface GameState {
  tick: number;
  /** Seconds of simulated game time elapsed. */
  time: number;
  speed: SpeedPreset;
  rngState: number;
  players: Player[];
  bases: Base[];
  units: Unit[];
  territories: Territory[];
  nextEntityId: EntityId;
  /** Transient visual events consumed by the renderer each frame. */
  effects: Effect[];
  /** Human-readable log surfaced in the UI. */
  log: LogEntry[];
  winner: number | null;
}

export interface Effect {
  kind: 'shot' | 'explosion' | 'build' | 'claim';
  x: number;
  y: number;
  tx?: number;
  ty?: number;
  /** Remaining lifetime in seconds. */
  life: number;
  scale: number;
  colour: string;
}

export interface LogEntry {
  time: number;
  text: string;
  severity: 'info' | 'good' | 'bad';
}

export function emptyResources(): Resources {
  return { steel: 0, aluminum: 0, fuel: 0 };
}

export function addResources(a: Resources, b: Resources): Resources {
  return {
    steel: a.steel + b.steel,
    aluminum: a.aluminum + b.aluminum,
    fuel: a.fuel + b.fuel,
  };
}

export function canAfford(have: Resources, cost: Resources): boolean {
  return have.steel >= cost.steel && have.aluminum >= cost.aluminum && have.fuel >= cost.fuel;
}

export function spend(have: Resources, cost: Resources): void {
  have.steel -= cost.steel;
  have.aluminum -= cost.aluminum;
  have.fuel -= cost.fuel;
}

export function playerById(state: GameState, id: PlayerId): Player | undefined {
  return state.players.find((p) => p.id === id);
}

export function baseById(state: GameState, id: EntityId): Base | undefined {
  return state.bases.find((b) => b.id === id);
}

export function unitById(state: GameState, id: EntityId): Unit | undefined {
  return state.units.find((u) => u.id === id);
}

/** Relationship between two players, driving targeting and UI colour. */
export function diplomacy(state: GameState, a: PlayerId, b: PlayerId): Diplomacy {
  if (a === b) return 'self';
  const pa = playerById(state, a);
  const pb = playerById(state, b);
  if (!pa || !pb) return 'neutral';
  return pa.team === pb.team ? 'ally' : 'enemy';
}

export function isHostile(state: GameState, a: PlayerId, b: PlayerId): boolean {
  return diplomacy(state, a, b) === 'enemy';
}

export function pushLog(state: GameState, text: string, severity: LogEntry['severity'] = 'info'): void {
  state.log.push({ time: state.time, text, severity });
  if (state.log.length > 200) state.log.shift();
}

export function addEffect(state: GameState, effect: Effect): void {
  state.effects.push(effect);
  if (state.effects.length > 600) state.effects.shift();
}
