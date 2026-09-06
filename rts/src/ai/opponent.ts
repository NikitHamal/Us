import type { GameState, Base, Player, PlayerId } from '../core/state';
import type { WorldMap } from '../core/worldmap';
import type { Command } from '../sim/commands';
import { Rng } from '../core/rng';
import { UNITS, UNIT_BY_ID, canProduceAt } from '../data/units';
import { BUILDING_BY_ID, upgradeCost } from '../data/buildings';
import { requiredMilitaryLevel } from '../sim/production';
import { canAfford } from '../core/state';
import { BASE_TYPE_BY_KIND } from '../data/bases';
import { offensiveSpecs, profileOf } from '../data/combat';

/**
 * The AI opponent.
 *
 * It plays the same game the human does -- it can only emit Commands, has no privileged
 * information, and pays the same costs. Its competence comes from a priority-ordered
 * economic plan plus composition-aware army building, not from cheating.
 */

export type Difficulty = 'recruit' | 'officer' | 'general';

export interface DifficultySettings {
  /** Seconds between AI decision passes. Lower is sharper. */
  thinkInterval: number;
  /** Fraction of income the AI is willing to commit to military. */
  militaryRatio: number;
  /** Army strength needed before it commits to an attack. */
  attackThreshold: number;
  /** Multiplier on how well it counters the player's composition. */
  counterplay: number;
}

export const DIFFICULTIES: Record<Difficulty, DifficultySettings> = {
  recruit: { thinkInterval: 8, militaryRatio: 0.4, attackThreshold: 2.0, counterplay: 0.2 },
  officer: { thinkInterval: 5, militaryRatio: 0.55, attackThreshold: 1.4, counterplay: 0.6 },
  general: { thinkInterval: 3, militaryRatio: 0.7, attackThreshold: 1.1, counterplay: 1.0 },
};

/** The economic build order the AI works through, highest priority first. */
const BUILD_PRIORITY: string[] = [
  'power_plant',
  'steel_mine',
  'aluminum_mine',
  'command_base',
  // Military Central sits high deliberately: it gates both unit tiers and unit slots,
  // so an AI that neglects it stays stuck fielding light tanks all match.
  'military_central',
  'fuel_pump',
  'factory',
  'steel_depot',
  'aluminum_depot',
  'research_lab',
  'defense_tower',
  'fuel_depot',
  'radar',
  'wall',
  'repair_bay',
];

export class AiOpponent {
  private readonly rng: Rng;
  private nextThink = 0;

  readonly playerId: PlayerId;
  readonly difficulty: Difficulty;

  constructor(playerId: PlayerId, difficulty: Difficulty, seed: number) {
    this.playerId = playerId;
    this.difficulty = difficulty;
    this.rng = new Rng(seed ^ (playerId * 2654435761));
  }

  private get settings(): DifficultySettings {
    return DIFFICULTIES[this.difficulty];
  }

  /** Called every frame; emits commands only on its own cadence. */
  update(state: GameState, map: WorldMap): Command[] {
    if (state.time < this.nextThink || state.winner !== null) return [];
    this.nextThink = state.time + this.settings.thinkInterval;

    const player = state.players.find((p) => p.id === this.playerId);
    if (!player || player.defeated) return [];

    const bases = state.bases.filter((b) => b.owner === this.playerId);
    if (bases.length === 0) return [];

    const commands: Command[] = [];
    commands.push(...this.manageEconomy(state, player, bases));
    commands.push(...this.manageResearch(state, player));
    commands.push(...this.manageArmy(state, player, bases));
    commands.push(...this.manageExpansion(state, player, map));
    commands.push(...this.manageOffensive(state, player));
    return commands;
  }

  /**
   * Spends on the highest-priority affordable upgrade across all its bases.
   *
   * Economy has first call on the treasury. Military spending is deliberately capped
   * to the surplus left over (see `reserveFor`), because an AI that spends every ingot
   * on cheap tanks never upgrades its Command Base and therefore never unlocks
   * anything -- which is exactly the failure this ordering prevents.
   */
  private manageEconomy(_state: GameState, player: Player, bases: Base[]): Command[] {
    const out: Command[] = [];

    for (const base of bases) {
      const busy = base.buildings.filter((b) => b.upgrading).length;
      if (busy >= 2) continue;

      // Score every candidate instead of taking the first affordable one. A strict
      // priority list means the cheapest entry (the Power Plant) is always affordable
      // first and is upgraded forever, starving everything below it.
      let bestId: string | null = null;
      let bestScore = -Infinity;

      for (let i = 0; i < BUILD_PRIORITY.length; i++) {
        const buildingId = BUILD_PRIORITY[i];
        const def = BUILDING_BY_ID[buildingId];
        if (!def || !def.allowedBases.includes(base.kind)) continue;
        const existing = base.buildings.find((b) => b.defId === buildingId);
        if (existing?.upgrading) continue;
        const target = (existing?.level ?? 0) + 1;
        if (target > def.maxLevel) continue;
        if (def.kind !== 'command' && target > base.cache.commandLevel) continue;

        const cost = upgradeCost(def, target);
        if (!canAfford(player.resources, cost)) continue;

        // Prefer entries that are high in the priority list and lagging behind the
        // rest of the base, so the whole installation levels up evenly.
        const priority = BUILD_PRIORITY.length - i;
        const lag = Math.max(1, base.cache.commandLevel + 1 - target);
        const score = priority * 2 + lag * 3 + this.rng.range(0, 2);
        if (score > bestScore) {
          bestScore = score;
          bestId = buildingId;
        }
      }

      if (bestId) {
        out.push({ type: 'build', player: this.playerId, baseId: base.id, buildingId: bestId });
      }
    }
    return out;
  }

  private manageResearch(state: GameState, player: Player): Command[] {
    if (player.researching) return [];
    // Research is a luxury: it may not eat the economy reserve either.
    const home = state.bases.find((b) => b.owner === this.playerId && b.kind === 'home');
    if (home) {
      const reserve = this.reserveFor(home);
      if (player.resources.steel < reserve.steel * 2) return [];
    }
    const hasLab = state.bases.some(
      (b) => b.owner === this.playerId && b.buildings.some((x) => x.defId === 'research_lab' && x.level > 0),
    );
    if (!hasLab) return [];
    const lines = ['extraction', 'weapons', 'armor', 'logistics', 'targeting', 'engines'];
    const weights = lines.map((id) => 1 / (1 + (player.research[id] ?? 0)));
    const totalWeight = weights.reduce((a, b) => a + b, 0);
    let roll = this.rng.next() * totalWeight;
    for (let i = 0; i < lines.length; i++) {
      roll -= weights[i];
      if (roll <= 0) {
        return [{ type: 'research', player: this.playerId, researchId: lines[i] }];
      }
    }
    return [];
  }

  /**
   * Builds units that counter what the enemy is actually fielding.
   * This is where the specialisation table earns its keep on the AI side too.
   */
  private manageArmy(state: GameState, player: Player, bases: Base[]): Command[] {
    const out: Command[] = [];
    const threat = this.enemyComposition(state);

    for (const base of bases) {
      // Always empty the garrison first. Garrisoned units still consume slots, so
      // leaving them parked silently caps the whole army.
      for (const [defId, count] of Object.entries(base.garrison)) {
        if (count > 0) {
          out.push({ type: 'deploy', player: this.playerId, baseId: base.id, unitDefId: defId, count });
        }
      }

      if (base.queue.length >= base.cache.queueSlots * 2) continue;
      const military = base.buildings.find((b) => b.defId === 'military_central');
      if (!military || military.level < 1) continue;

      // Military may only spend the surplus above the economy reserve.
      const reserve = this.reserveFor(base);
      const ratio = this.settings.militaryRatio;
      const budget = {
        steel: Math.max(0, player.resources.steel - reserve.steel) * ratio,
        aluminum: Math.max(0, player.resources.aluminum - reserve.aluminum) * ratio,
        fuel: Math.max(0, player.resources.fuel - reserve.fuel) * ratio,
      };
      const options = UNITS.filter(
        (u) =>
          canProduceAt(base.kind, u) &&
          u.damage > 0 &&
          military.level >= requiredMilitaryLevel(u.id) &&
          canAfford(budget, { steel: u.cost.steel, aluminum: u.cost.aluminum, fuel: 0 }),
      );
      if (options.length === 0) continue;

      // Score by counter value against observed enemy composition, plus raw efficiency.
      let bestId = options[0].id;
      let bestScore = -Infinity;
      for (const u of options) {
        const total = u.cost.steel + u.cost.aluminum;
        const efficiency = Math.pow(u.damage * (1 + u.armor / 2000), 0.5) / Math.max(1, Math.pow(total / 400000, 0.35));
        let counter = 0;
        for (const spec of offensiveSpecs(u)) {
          counter += (threat[spec] ?? 0) * 5;
        }
        const score = efficiency + counter * this.settings.counterplay * 40 + this.rng.range(0, 8);
        if (score > bestScore) {
          bestScore = score;
          bestId = u.id;
        }
      }
      // Queue a batch sized to what the surplus can actually sustain.
      const bestDef = UNIT_BY_ID[bestId];
      const perUnit = Math.max(1, bestDef.cost.steel + bestDef.cost.aluminum);
      const surplus = budget.steel + budget.aluminum;
      const batch = Math.max(1, Math.min(4, Math.floor(surplus / perUnit)));
      out.push({ type: 'queueUnit', player: this.playerId, baseId: base.id, unitDefId: bestId, count: batch });
    }
    return out;
  }

  /** Tallies what fraction of enemy strength falls into each counterable category. */
  private enemyComposition(state: GameState): Record<string, number> {
    const tally: Record<string, number> = {};
    let total = 0;
    for (const u of state.units) {
      const p = state.players.find((x) => x.id === u.owner);
      const me = state.players.find((x) => x.id === this.playerId);
      if (!p || !me || p.team === me.team) continue;
      const def = UNIT_BY_ID[u.defId];
      if (!def) continue;
      const profile = profileOf(def);
      const keys: string[] = [];
      if (profile.domain === 'ground') keys.push('vs_vehicles');
      if (profile.domain === 'naval') keys.push('vs_boats');
      if (profile.domain === 'rail') keys.push('vs_trains');
      if (profile.domain === 'air') {
        keys.push('vs_air');
        keys.push(profile.isCopter ? 'vs_copters' : 'vs_aircraft');
      }
      for (const k of keys) tally[k] = (tally[k] ?? 0) + 1;
      total++;
    }
    if (total > 0) {
      for (const k of Object.keys(tally)) tally[k] /= total;
    }
    // Always keep some anti-base capability in the mix.
    tally.vs_bases = 0.25;
    return tally;
  }

  /** Claims nearby neutral territory and founds specialised bases when it can. */
  private manageExpansion(state: GameState, player: Player, _map: WorldMap): Command[] {
    const out: Command[] = [];
    const myBases = state.bases.filter((b) => b.owner === this.playerId);
    if (myBases.length === 0) return out;

    const apcs = state.units.filter(
      (u) => u.owner === this.playerId && UNIT_BY_ID[u.defId]?.specialisations.includes('apc') && u.order.kind === 'idle',
    );

    const neutral = state.territories
      .filter((t) => t.owner === null)
      .sort(
        (a, b) =>
          this.distToNearestBase(myBases, a.wx, a.wy) - this.distToNearestBase(myBases, b.wx, b.wy),
      );

    for (let i = 0; i < Math.min(apcs.length, neutral.length); i++) {
      out.push({
        type: 'claim',
        player: this.playerId,
        unitIds: [apcs[i].id],
        territoryId: neutral[i].id,
      });
    }

    // Keep a small claiming detachment rather than an endless truck convoy.
    const allApcs = state.units.filter(
      (u) => u.owner === this.playerId && UNIT_BY_ID[u.defId]?.specialisations.includes('apc'),
    ).length;
    const queuedApcs = myBases.reduce(
      (n, b) => n + b.queue.filter((q) => UNIT_BY_ID[q.unitDefId]?.specialisations.includes('apc')).length,
      0,
    );
    const wantApcs = Math.min(4, neutral.length);
    if (allApcs + queuedApcs < wantApcs) {
      const tankBase = myBases.find((b) => b.kind === 'tank') ?? myBases.find((b) => b.kind === 'home');
      const apc = UNIT_BY_ID['conquest_truck'];
      if (tankBase && canAfford(player.resources, { steel: apc.cost.steel, aluminum: apc.cost.aluminum, fuel: 0 })) {
        out.push({ type: 'queueUnit', player: this.playerId, baseId: tankBase.id, unitDefId: apc.id, count: 1 });
      }
    }

    // Found a new specialised base on owned territory.
    const owned = state.territories.filter(
      (t) => t.owner === this.playerId && !state.bases.some((b) => Math.hypot(b.wx - t.wx, b.wy - t.wy) < 12),
    );
    if (owned.length > 0) {
      const counts = new Map<string, number>();
      for (const b of myBases) counts.set(b.kind, (counts.get(b.kind) ?? 0) + 1);
      // Tank bases first -- they unlock the bulk of the roster -- then the rest.
      const order = ['tank', 'air', 'helicopter', 'train', 'harbor'] as const;
      const wanted = order.find(
        (k) => (counts.get(k) ?? 0) < 2 && owned.some((t) => t.allows.includes(k)),
      );
      if (wanted) {
        const site = owned.find((t) => t.allows.includes(wanted));
        if (site && canAfford(player.resources, BASE_TYPE_BY_KIND[wanted].foundingCost)) {
          out.push({ type: 'foundBase', player: this.playerId, territoryId: site.id, kind: wanted });
        }
      }
    }
    return out;
  }

  /**
   * Resources the AI refuses to spend on units.
   *
   * Sized as a fixed window of income rather than as a multiple of the next Command
   * Base upgrade: upgrade costs grow geometrically while income grows more slowly, so
   * a cost-based reserve eventually swallows the entire treasury and the AI stops
   * building an army altogether.
   */
  private reserveFor(base: Base): { steel: number; aluminum: number; fuel: number } {
    const seconds = 90;
    return {
      steel: base.cache.income.steel * seconds,
      aluminum: base.cache.income.aluminum * seconds,
      fuel: base.cache.income.fuel * seconds,
    };
  }

  private distToNearestBase(bases: Base[], x: number, y: number): number {
    return Math.min(...bases.map((b) => Math.hypot(b.wx - x, b.wy - y)));
  }

  /** Commits the army once it outweighs the defender, otherwise holds near home. */
  private manageOffensive(state: GameState, player: Player): Command[] {
    const army = state.units.filter(
      (u) => u.owner === this.playerId && (UNIT_BY_ID[u.defId]?.damage ?? 0) > 0,
    );
    if (army.length < 4) return [];

    const myStrength = this.strengthOf(state, this.playerId);
    const enemies = state.players.filter((p) => p.team !== player.team && !p.defeated);
    if (enemies.length === 0) return [];

    const target = enemies
      .map((e) => ({ player: e, strength: this.strengthOf(state, e.id) }))
      .sort((a, b) => a.strength - b.strength)[0];

    if (myStrength < target.strength * this.settings.attackThreshold) {
      return [];
    }

    const targetBases = state.bases.filter((b) => b.owner === target.player.id);
    if (targetBases.length === 0) return [];
    const idleArmy = army.filter((u) => u.order.kind === 'idle' || u.order.kind === 'move');
    if (idleArmy.length === 0) return [];

    // Concentrate on the closest enemy base rather than splitting the army.
    const focus = targetBases.sort(
      (a, b) =>
        this.distToNearestBase(
          state.bases.filter((x) => x.owner === this.playerId), a.wx, a.wy) -
        this.distToNearestBase(
          state.bases.filter((x) => x.owner === this.playerId), b.wx, b.wy),
    )[0];

    return [
      {
        type: 'attackBase',
        player: this.playerId,
        unitIds: idleArmy.map((u) => u.id),
        baseId: focus.id,
      },
    ];
  }

  private strengthOf(state: GameState, playerId: PlayerId): number {
    let sum = 0;
    for (const u of state.units) {
      if (u.owner !== playerId) continue;
      const def = UNIT_BY_ID[u.defId];
      if (!def) continue;
      sum += def.damage * (1 + def.armor / 2000) * (u.hp / Math.max(1, u.maxHp));
    }
    return sum;
  }
}

/** Builds AI controllers for every non-human player in a match. */
export function createOpponents(state: GameState, difficulty: Difficulty, seed: number): AiOpponent[] {
  return state.players
    .filter((p) => !p.isHuman)
    .map((p) => new AiOpponent(p.id, difficulty, seed));
}
