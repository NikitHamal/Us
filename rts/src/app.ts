import './ui/style.css';
import { createMatch, humanPlayer } from './sim/world';
import { Simulation } from './sim/loop';
import { createOpponents } from './ai/opponent';
import type { AiOpponent } from './ai/opponent';
import { Camera } from './render/camera';
import { SceneRenderer, bindContext } from './render/scene';
import { Hud } from './ui/hud';
import { BasePanel } from './ui/basepanel';
import { Minimap } from './ui/minimap';
import { SelectionBar } from './ui/selectionbar';
import { InputController } from './ui/input';
import { showSetup, showResult } from './ui/setup';
import type { SetupResult } from './ui/setup';
import { SPEED_OPTIONS } from './data/config';
import type { Command } from './sim/commands';
import { UNIT_BY_ID } from './data/units';
import type { WorldMap } from './core/worldmap';

/**
 * Application shell.
 *
 * Owns the canvas, the render loop, and the glue between UI intents and simulation
 * commands. Everything it does to the world goes through Simulation.enqueue, so the
 * human player has exactly the same interface to the game that the AI does.
 */

class Game {
  private readonly root: HTMLElement;
  private canvas!: HTMLCanvasElement;
  private ctx!: CanvasRenderingContext2D;
  private camera = new Camera();
  private sim!: Simulation;
  private scene!: SceneRenderer;
  private opponents: AiOpponent[] = [];

  private hud!: Hud;
  private panel!: BasePanel;
  private minimap!: Minimap;
  private selectionBar!: SelectionBar;
  private input!: InputController;
  private hudLayer!: HTMLElement;

  private selected = new Set<number>();
  private selectedBaseId: number | null = null;
  private viewerId = 0;
  private lastFrame = 0;
  private elapsed = 0;
  private running = false;
  private resultShown = false;

  constructor(root: HTMLElement) {
    this.root = root;
  }

  async start(): Promise<void> {
    const setup = await showSetup(this.root);
    this.begin(setup);
  }

  private begin(setup: SetupResult): void {
    this.root.innerHTML = '';
    this.selected.clear();
    this.selectedBaseId = null;
    this.resultShown = false;

    const match = createMatch(setup);
    this.sim = new Simulation(match.state, match.map);
    this.sim.setSpeed(setup.speed);
    this.opponents = createOpponents(match.state, setup.difficulty, setup.seed);
    this.viewerId = humanPlayer(match.state).id;

    this.buildDom(match.map);
    this.scene = new SceneRenderer(match.map);

    // Open on the player's home base.
    const home = match.state.bases.find((b) => b.owner === this.viewerId);
    if (home) {
      this.camera.centreOn(home.wx, home.wy);
      this.selectedBaseId = home.id;
    }
    this.camera.zoom = 0.85;

    this.running = true;
    this.lastFrame = performance.now();
    requestAnimationFrame(this.frame);
  }

  private buildDom(map: WorldMap): void {
    this.canvas = document.createElement('canvas');
    this.canvas.id = 'viewport';
    this.root.appendChild(this.canvas);
    const ctx = this.canvas.getContext('2d', { alpha: false });
    if (!ctx) throw new Error('Canvas 2D context unavailable.');
    this.ctx = ctx;

    this.hudLayer = document.createElement('div');
    this.hudLayer.className = 'hud';
    this.root.appendChild(this.hudLayer);

    this.hud = new Hud(this.hudLayer, {
      onSpeedChange: (speed) => this.sim.setSpeed(speed),
      onTogglePause: () => this.sim.togglePause(),
    });

    this.panel = new BasePanel(this.hudLayer, {
      onBuild: (baseId, buildingId) =>
        this.send({ type: 'build', player: this.viewerId, baseId, buildingId }),
      onQueueUnit: (baseId, unitDefId, count) =>
        this.send({ type: 'queueUnit', player: this.viewerId, baseId, unitDefId, count }),
      onCancelQueue: (baseId, index) =>
        this.send({ type: 'cancelQueue', player: this.viewerId, baseId, index }),
      onResearch: (researchId) =>
        this.send({ type: 'research', player: this.viewerId, researchId }),
      onDeploy: (baseId, unitDefId, count) =>
        this.send({ type: 'deploy', player: this.viewerId, baseId, unitDefId, count }),
      onSelectBase: (baseId) => this.focusBase(baseId),
    });

    this.minimap = new Minimap(this.hudLayer, map, (x, y) => this.camera.centreOn(x, y));

    this.selectionBar = new SelectionBar(this.hudLayer, {
      onSelectSubset: (ids) => {
        this.selected = new Set(ids);
      },
      onStop: () =>
        this.send({ type: 'stop', player: this.viewerId, unitIds: [...this.selected] }),
      onRecall: () =>
        this.send({ type: 'recall', player: this.viewerId, unitIds: [...this.selected] }),
    });

    this.input = new InputController(this.canvas, this.camera, () => this.sim.state, this.viewerId, {
      onSelect: (ids, additive) => {
        const owned = ids.filter(
          (id) => this.sim.state.units.find((u) => u.id === id)?.owner === this.viewerId,
        );
        if (owned.length === 0 && !additive) return;
        if (additive) for (const id of owned) this.selected.add(id);
        else this.selected = new Set(owned);
        if (this.selected.size > 0) this.selectedBaseId = this.selectedBaseId;
      },
      onSelectBase: (baseId) => {
        const base = this.sim.state.bases.find((b) => b.id === baseId);
        if (base?.owner === this.viewerId) {
          this.selectedBaseId = baseId;
          this.selected.clear();
        }
      },
      onOrderMove: (x, y) =>
        this.selected.size > 0 &&
        this.send({ type: 'move', player: this.viewerId, unitIds: [...this.selected], x, y }),
      onOrderAttackUnit: (targetId) =>
        this.selected.size > 0 &&
        this.send({ type: 'attackUnit', player: this.viewerId, unitIds: [...this.selected], targetId }),
      onOrderAttackBase: (baseId) =>
        this.selected.size > 0 &&
        this.send({ type: 'attackBase', player: this.viewerId, unitIds: [...this.selected], baseId }),
      onClaim: (territoryId) =>
        this.selected.size > 0 &&
        this.send({ type: 'claim', player: this.viewerId, unitIds: [...this.selected], territoryId }),
      onDeselect: () => {
        this.selected.clear();
      },
      onTogglePause: () => this.sim.togglePause(),
      onCycleSpeed: () => {
        const i = SPEED_OPTIONS.findIndex((s) => s.id === this.sim.state.speed);
        this.sim.setSpeed(SPEED_OPTIONS[(i + 1) % SPEED_OPTIONS.length].id);
      },
      onSelectAllArmy: () => {
        this.selected = new Set(
          this.sim.state.units
            .filter((u) => u.owner === this.viewerId && (UNIT_BY_ID[u.defId]?.damage ?? 0) > 0)
            .map((u) => u.id),
        );
      },
    });

    window.addEventListener('resize', () => this.resize());
    this.resize();
  }

  private focusBase(baseId: number): void {
    const base = this.sim.state.bases.find((b) => b.id === baseId);
    if (!base || base.owner !== this.viewerId) return;
    this.selectedBaseId = baseId;
    this.camera.centreOn(base.wx, base.wy);
  }

  private send(cmd: Command): void {
    this.sim.enqueue(cmd);
  }

  private resize(): void {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const w = window.innerWidth;
    const h = window.innerHeight;
    this.canvas.width = Math.floor(w * dpr);
    this.canvas.height = Math.floor(h * dpr);
    this.canvas.style.width = `${w}px`;
    this.canvas.style.height = `${h}px`;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.camera.resize(w, h);
    this.scene?.invalidateTerrain();
  }

  private frame = (now: number): void => {
    if (!this.running) return;
    const dt = Math.min(0.1, (now - this.lastFrame) / 1000);
    this.lastFrame = now;
    this.elapsed += dt;

    this.input.updateCameraKeys(dt);

    // AI thinks before the simulation ticks, so its commands land this frame.
    if (!this.sim.isPaused) {
      for (const ai of this.opponents) {
        for (const cmd of ai.update(this.sim.state, this.sim.map)) this.sim.enqueue(cmd);
      }
    }
    this.sim.advance(dt);
    this.pruneSelection();

    bindContext(this.ctx);
    this.scene.render(this.ctx, {
      state: this.sim.state,
      map: this.sim.map,
      camera: this.camera,
      selected: this.selected,
      selectedBaseId: this.selectedBaseId,
      viewer: this.viewerId,
      marquee: this.input.marquee,
      time: this.elapsed,
      hover: this.input.hover,
    });

    const base = this.sim.state.bases.find((b) => b.id === this.selectedBaseId) ?? null;
    this.hud.update(this.sim.state, this.viewerId, this.sim.isPaused);
    this.panel.update(this.sim.state, this.viewerId, base, this.elapsed);
    this.minimap.update(this.sim.state, this.camera, this.viewerId);
    this.selectionBar.update(this.sim.state, this.selected);

    this.checkResult();
    requestAnimationFrame(this.frame);
  };

  /** Drops destroyed units from the selection so the bar never shows ghosts. */
  private pruneSelection(): void {
    if (this.selected.size === 0) return;
    const alive = new Set(this.sim.state.units.map((u) => u.id));
    for (const id of this.selected) if (!alive.has(id)) this.selected.delete(id);
  }

  private checkResult(): void {
    if (this.resultShown || this.sim.state.winner === null) return;
    this.resultShown = true;
    const human = this.sim.state.players.find((p) => p.isHuman);
    showResult(this.root, human?.team === this.sim.state.winner, () => {
      this.running = false;
      this.root.innerHTML = '';
      void new Game(this.root).start();
    });
  }
}

const root = document.getElementById('app');
if (root) void new Game(root).start();
