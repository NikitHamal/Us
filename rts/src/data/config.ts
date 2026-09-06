/**
 * Global tuning constants and the configurable game-speed presets.
 *
 * Desert Order is a browser persistent-world game where a single upgrade can take real
 * hours. That pacing is authentic but unplayable in a session, so speed is a first-class
 * setting: every duration in the simulation is divided by the active multiplier.
 */

export type SpeedPreset = 'authentic' | 'fast' | 'blitz';

export interface SpeedOption {
  id: SpeedPreset;
  label: string;
  /** Durations are divided by this; resource income is multiplied by it. */
  multiplier: number;
  description: string;
}

export const SPEED_OPTIONS: SpeedOption[] = [
  {
    id: 'authentic',
    label: 'Authentic',
    multiplier: 1,
    description: 'Original Desert Order pacing. Upgrades take real time; play in sessions.',
  },
  {
    id: 'fast',
    label: 'Fast',
    multiplier: 12,
    description: 'Recommended. A full match runs in about an hour with the same balance.',
  },
  {
    id: 'blitz',
    label: 'Blitz',
    multiplier: 45,
    description: 'Everything is near-instant. Best for testing army compositions.',
  },
];

export const DEFAULT_SPEED: SpeedPreset = 'fast';

export function speedMultiplier(preset: SpeedPreset): number {
  return SPEED_OPTIONS.find((s) => s.id === preset)?.multiplier ?? 1;
}

/** Simulation runs on a fixed timestep so results are deterministic and replayable. */
export const TICK_HZ = 20;
export const TICK_SECONDS = 1 / TICK_HZ;

/** World map dimensions in tiles. Deliberately very large, as requested. */
export const MAP_WIDTH = 512;
export const MAP_HEIGHT = 512;

/** Isometric tile dimensions in screen pixels at zoom 1. */
export const TILE_W = 64;
export const TILE_H = 32;

/** Starting resources for every player. */
export const STARTING_RESOURCES = {
  steel: 400000,
  aluminum: 140000,
  fuel: 60000,
};

/** Storage ceiling before any depots are built. */
export const BASE_STORAGE = 1500000;

/** A unit that runs dry cannot move until it returns to a base or is resupplied. */
export const FUEL_TANK_SECONDS = 180;

/** How close a unit must be to a base to refuel and repair. */
export const RESUPPLY_RADIUS = 4;

/** Repair rate as a fraction of max HP per second while stationed in a repair bay. */
export const REPAIR_RATE = 0.02;

/** Attacks per second. Applied uniformly; damage values carry the real balance. */
export const FIRE_RATE = 0.6;

/** Territory a Conquest Truck claims, in tiles of radius. */
export const CLAIM_RADIUS = 6;
export const CLAIM_SECONDS = 20;
