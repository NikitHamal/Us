import type { GameState } from '../core/state';
import type { WorldMap } from '../core/worldmap';
import type { Command, CommandContext } from './commands';
import { applyCommand, tickClaims } from './commands';
import { tickEconomy } from './economy';
import { tickConstruction, tickProduction, tickResearch } from './production';
import { tickMovement, tickResupply } from './movement';
import { tickCombat, tickTurrets } from './combat';
import { checkDefeat } from './world';
import { TICK_SECONDS, speedMultiplier } from '../data/config';
import type { SpeedPreset } from '../data/config';

/**
 * The fixed-timestep simulation loop.
 *
 * Real elapsed time is accumulated and consumed in fixed TICK_SECONDS slices, so the
 * simulation advances identically regardless of frame rate. Commands are queued and
 * drained at tick boundaries, never mid-tick, which is what keeps the whole thing
 * deterministic and therefore replayable and server-verifiable.
 */

export class Simulation {
  readonly state: GameState;
  readonly map: WorldMap;

  private accumulator = 0;
  private pending: Command[] = [];
  private paused = false;

  /** Ticks simulated in the last frame; surfaced for a performance readout. */
  lastTicks = 0;

  constructor(state: GameState, map: WorldMap) {
    this.state = state;
    this.map = map;
  }

  get isPaused(): boolean {
    return this.paused;
  }

  setPaused(paused: boolean): void {
    this.paused = paused;
  }

  togglePause(): void {
    this.paused = !this.paused;
  }

  setSpeed(speed: SpeedPreset): void {
    this.state.speed = speed;
  }

  /** Queues a command for the next tick boundary. */
  enqueue(cmd: Command): void {
    this.pending.push(cmd);
  }

  /**
   * Advances the simulation by `realSeconds` of wall-clock time.
   * A cap prevents a long stall (tab in the background) from causing a huge catch-up
   * spiral that would freeze the browser.
   */
  advance(realSeconds: number): void {
    if (this.paused || this.state.winner !== null) {
      this.pending = [];
      this.lastTicks = 0;
      return;
    }

    const scale = speedMultiplier(this.state.speed);
    this.accumulator += Math.min(realSeconds, 0.25) * scale;

    let ticks = 0;
    const maxTicksPerFrame = 200;
    while (this.accumulator >= TICK_SECONDS && ticks < maxTicksPerFrame) {
      this.accumulator -= TICK_SECONDS;
      this.tick(TICK_SECONDS);
      ticks++;
    }
    // Discard any backlog we could not consume, rather than accumulating debt.
    if (ticks >= maxTicksPerFrame) this.accumulator = 0;
    this.lastTicks = ticks;
  }

  private tick(dt: number): void {
    const state = this.state;
    const ctx: CommandContext = { state, map: this.map };

    if (this.pending.length > 0) {
      const batch = this.pending;
      this.pending = [];
      for (const cmd of batch) applyCommand(ctx, cmd);
    }

    tickEconomy(state, dt);
    tickConstruction(state, dt);
    tickProduction(state, dt);
    tickResearch(state, dt);
    tickMovement(state, this.map, dt);
    tickResupply(state, dt);
    tickClaims(state, dt);
    tickCombat(state, dt);
    tickTurrets(state, dt);
    decayEffects(state, dt);

    state.tick++;
    state.time += dt;

    // Defeat conditions are expensive to evaluate; once a second is plenty.
    if (state.tick % 20 === 0) checkDefeat(state);
  }
}

/** Ages out transient visual effects. */
function decayEffects(state: GameState, dt: number): void {
  if (state.effects.length === 0) return;
  for (const e of state.effects) e.life -= dt;
  state.effects = state.effects.filter((e) => e.life > 0);
}
