import type { Camera } from '../render/camera';
import type { GameState } from '../core/state';
import { UNIT_BY_ID } from '../data/units';
import { BASE_TYPE_BY_KIND } from '../data/bases';

/**
 * Mouse and keyboard input.
 *
 * Emits high-level intents (select these units, order a move here, click this base)
 * so the app layer never has to think about pixels or drag thresholds.
 */

export interface InputIntents {
  onSelect: (unitIds: number[], additive: boolean) => void;
  onSelectBase: (baseId: number) => void;
  onOrderMove: (wx: number, wy: number) => void;
  onOrderAttackUnit: (unitId: number) => void;
  onOrderAttackBase: (baseId: number) => void;
  onClaim: (territoryId: number) => void;
  onDeselect: () => void;
  onTogglePause: () => void;
  onCycleSpeed: () => void;
  onSelectAllArmy: () => void;
}

const DRAG_THRESHOLD = 5;

export class InputController {
  marquee: { x0: number; y0: number; x1: number; y1: number } | null = null;
  hover: { x: number; y: number } | null = null;

  private dragging = false;
  private panning = false;
  private downAt = { x: 0, y: 0 };
  private lastPan = { x: 0, y: 0 };
  private readonly keys = new Set<string>();

  private readonly canvas: HTMLCanvasElement;
  private readonly camera: Camera;
  private readonly getState: () => GameState;
  private readonly viewerId: number;
  private readonly intents: InputIntents;

  constructor(
    canvas: HTMLCanvasElement,
    camera: Camera,
    getState: () => GameState,
    viewerId: number,
    intents: InputIntents,
  ) {
    this.canvas = canvas;
    this.camera = camera;
    this.getState = getState;
    this.viewerId = viewerId;
    this.intents = intents;
    this.attach();
  }

  private attach(): void {
    const c = this.canvas;
    c.addEventListener('mousedown', this.onMouseDown);
    window.addEventListener('mousemove', this.onMouseMove);
    window.addEventListener('mouseup', this.onMouseUp);
    c.addEventListener('wheel', this.onWheel, { passive: false });
    c.addEventListener('contextmenu', (e) => e.preventDefault());
    window.addEventListener('keydown', this.onKeyDown);
    window.addEventListener('keyup', this.onKeyUp);
  }

  private onMouseDown = (e: MouseEvent): void => {
    const p = this.localPoint(e);
    this.downAt = p;
    if (e.button === 0) {
      this.dragging = true;
      this.marquee = { x0: p.x, y0: p.y, x1: p.x, y1: p.y };
    } else if (e.button === 2 || e.button === 1) {
      // Right-drag pans; a right-click without movement issues an order.
      this.panning = true;
      this.lastPan = p;
    }
  };

  private onMouseMove = (e: MouseEvent): void => {
    const p = this.localPoint(e);
    const world = this.camera.screenToWorld(p.x, p.y);
    this.hover = world;

    if (this.dragging && this.marquee) {
      this.marquee.x1 = p.x;
      this.marquee.y1 = p.y;
    }
    if (this.panning) {
      this.camera.panBy(p.x - this.lastPan.x, p.y - this.lastPan.y);
      this.lastPan = p;
    }
  };

  private onMouseUp = (e: MouseEvent): void => {
    const p = this.localPoint(e);
    const moved = Math.hypot(p.x - this.downAt.x, p.y - this.downAt.y);

    if (e.button === 0 && this.dragging) {
      this.dragging = false;
      if (moved > DRAG_THRESHOLD && this.marquee) {
        this.selectInMarquee(this.marquee, e.shiftKey);
      } else {
        this.clickSelect(p, e.shiftKey);
      }
      this.marquee = null;
    }

    if ((e.button === 2 || e.button === 1) && this.panning) {
      this.panning = false;
      if (moved <= DRAG_THRESHOLD && e.button === 2) {
        this.issueOrder(p);
      }
    }
  };

  private onWheel = (e: WheelEvent): void => {
    e.preventDefault();
    const p = this.localPoint(e);
    this.camera.zoomAt(p.x, p.y, e.deltaY < 0 ? 1.12 : 1 / 1.12);
  };

  private onKeyDown = (e: KeyboardEvent): void => {
    if (e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement) return;
    this.keys.add(e.key.toLowerCase());
    if (e.code === 'Space') {
      e.preventDefault();
      this.intents.onTogglePause();
    }
    if (e.key === 'Escape') this.intents.onDeselect();
    if (e.key.toLowerCase() === 'a' && e.ctrlKey) {
      e.preventDefault();
      this.intents.onSelectAllArmy();
    }
    if (e.key === '`') this.intents.onCycleSpeed();
  };

  private onKeyUp = (e: KeyboardEvent): void => {
    this.keys.delete(e.key.toLowerCase());
  };

  /** Edge and WASD scrolling, called once per frame. */
  updateCameraKeys(dt: number): void {
    const speed = 900 * dt;
    let dx = 0;
    let dy = 0;
    if (this.keys.has('w') || this.keys.has('arrowup')) dy += speed;
    if (this.keys.has('s') || this.keys.has('arrowdown')) dy -= speed;
    if (this.keys.has('a') || this.keys.has('arrowleft')) dx += speed;
    if (this.keys.has('d') || this.keys.has('arrowright')) dx -= speed;
    if (dx !== 0 || dy !== 0) this.camera.panBy(dx, dy);
  }

  private localPoint(e: MouseEvent | WheelEvent): { x: number; y: number } {
    const r = this.canvas.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }

  /** Picks whatever is under a single click: unit first, then base, then ground. */
  private clickSelect(p: { x: number; y: number }, additive: boolean): void {
    const unit = this.unitAt(p);
    if (unit !== null) {
      this.intents.onSelect([unit], additive);
      return;
    }
    const base = this.baseAt(p);
    if (base !== null) {
      this.intents.onSelectBase(base);
      return;
    }
    if (!additive) this.intents.onDeselect();
  }

  /** Right-click: attack a hostile, claim a territory, or move. */
  private issueOrder(p: { x: number; y: number }): void {
    const state = this.getState();
    const viewer = state.players.find((x) => x.id === this.viewerId);

    const unitId = this.unitAt(p);
    if (unitId !== null) {
      const target = state.units.find((u) => u.id === unitId);
      const owner = target ? state.players.find((x) => x.id === target.owner) : undefined;
      if (target && owner && viewer && owner.team !== viewer.team) {
        this.intents.onOrderAttackUnit(unitId);
        return;
      }
    }

    const baseId = this.baseAt(p);
    if (baseId !== null) {
      const base = state.bases.find((b) => b.id === baseId);
      const owner = base ? state.players.find((x) => x.id === base.owner) : undefined;
      if (base && owner && viewer && owner.team !== viewer.team) {
        this.intents.onOrderAttackBase(baseId);
        return;
      }
    }

    const world = this.camera.screenToWorld(p.x, p.y);
    const territory = state.territories.find(
      (t) => t.owner !== this.viewerId && Math.hypot(t.wx - world.x, t.wy - world.y) < 3,
    );
    if (territory) {
      this.intents.onClaim(territory.id);
      return;
    }

    this.intents.onOrderMove(world.x, world.y);
  }

  /** Hit-tests units in screen space, nearest to the camera first. */
  private unitAt(p: { x: number; y: number }): number | null {
    const state = this.getState();
    const radius = Math.max(10, 22 * this.camera.zoom);
    let best: number | null = null;
    let bestDepth = -Infinity;
    for (const u of state.units) {
      const def = UNIT_BY_ID[u.defId];
      if (!def) continue;
      const s = this.camera.worldToScreen(u.x, u.y);
      if (Math.hypot(s.x - p.x, s.y - p.y) > radius) continue;
      const depth = u.x + u.y;
      if (depth > bestDepth) {
        bestDepth = depth;
        best = u.id;
      }
    }
    return best;
  }

  private baseAt(p: { x: number; y: number }): number | null {
    const state = this.getState();
    const world = this.camera.screenToWorld(p.x, p.y);
    for (const b of state.bases) {
      const grid = BASE_TYPE_BY_KIND[b.kind].grid;
      if (
        Math.abs(world.x - b.wx) <= grid.w / 2 &&
        Math.abs(world.y - b.wy) <= grid.h / 2
      ) {
        return b.id;
      }
    }
    return null;
  }

  /** Selects the player's own units inside the drag rectangle. */
  private selectInMarquee(
    m: { x0: number; y0: number; x1: number; y1: number },
    additive: boolean,
  ): void {
    const state = this.getState();
    const x0 = Math.min(m.x0, m.x1);
    const x1 = Math.max(m.x0, m.x1);
    const y0 = Math.min(m.y0, m.y1);
    const y1 = Math.max(m.y0, m.y1);
    const ids: number[] = [];
    for (const u of state.units) {
      if (u.owner !== this.viewerId) continue;
      const s = this.camera.worldToScreen(u.x, u.y);
      if (s.x >= x0 && s.x <= x1 && s.y >= y0 && s.y <= y1) ids.push(u.id);
    }
    this.intents.onSelect(ids, additive);
  }
}
