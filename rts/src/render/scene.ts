import type { GameState, Base } from '../core/state';
import type { WorldMap, Decoration } from '../core/worldmap';
import type { Camera } from './camera';
import { TerrainRenderer } from './terrain';
import { drawUnit, drawSelectionRing } from './sprites';
import { drawBase } from './structures';
import { UNIT_BY_ID } from '../data/units';
import { UI, withAlpha, VOID_COLOUR } from './palette';
import { TILE_W, TILE_H } from '../data/config';
import { canSee } from '../sim/combat';

/**
 * The scene renderer: composes terrain, decorations, bases, units and effects into
 * one correctly-depth-sorted isometric frame.
 *
 * Draw order is by world depth (x + y), which is what makes objects nearer the camera
 * correctly overlap those behind them without a z-buffer.
 */

interface Drawable {
  depth: number;
  draw: () => void;
}

export interface SceneInput {
  state: GameState;
  map: WorldMap;
  camera: Camera;
  /** Ids of units the human player has selected. */
  selected: Set<number>;
  selectedBaseId: number | null;
  /** Human player id, for visibility and colouring. */
  viewer: number;
  /** Live drag-selection rectangle in screen space, if any. */
  marquee: { x0: number; y0: number; x1: number; y1: number } | null;
  /** Wall-clock seconds, for animation. */
  time: number;
  /** World tile currently under the cursor. */
  hover: { x: number; y: number } | null;
}

export class SceneRenderer {
  private readonly terrain: TerrainRenderer;

  constructor(map: WorldMap) {
    this.terrain = new TerrainRenderer(map);
  }

  render(ctx: CanvasRenderingContext2D, input: SceneInput): void {
    const { camera, state, map, time } = input;

    ctx.fillStyle = VOID_COLOUR;
    ctx.fillRect(0, 0, camera.viewWidth, camera.viewHeight);

    this.terrain.draw(ctx, camera, time);

    const drawables: Drawable[] = [];
    this.collectDecorations(drawables, map, camera);
    this.collectTerritories(drawables, input);
    this.collectBases(drawables, input);
    this.collectUnits(drawables, input);

    drawables.sort((a, b) => a.depth - b.depth);
    for (const d of drawables) d.draw();

    this.drawEffects(ctx, input);
    this.drawMarquee(ctx, input);
    this.drawHover(ctx, input);
    void state;
  }

  /** Palms, rocks and wrecks. Culled to the viewport and thinned out when zoomed far. */
  private collectDecorations(out: Drawable[], map: WorldMap, camera: Camera): void {
    if (camera.zoom < 0.28) return;
    const b = camera.visibleBounds(4);
    const skip = camera.zoom < 0.5 ? 3 : 1;
    let n = 0;
    for (const dec of map.decorations) {
      if (dec.x < b.x0 || dec.x > b.x1 || dec.y < b.y0 || dec.y > b.y1) continue;
      if (skip > 1 && n++ % skip !== 0) continue;
      const p = camera.worldToScreen(dec.x, dec.y);
      out.push({
        depth: dec.x + dec.y,
        draw: () => drawDecoration(camera, p.x, p.y, dec),
      });
    }
  }

  private collectTerritories(out: Drawable[], input: SceneInput): void {
    const { state, camera } = input;
    for (const t of state.territories) {
      if (!camera.isVisible(t.wx, t.wy, 200)) continue;
      const owner = t.owner === null ? null : state.players.find((p) => p.id === t.owner);
      const p = camera.worldToScreen(t.wx, t.wy);
      out.push({
        depth: t.wx + t.wy - 0.5,
        draw: () => {
          const ctx = getCtx(input);
          const colour = owner?.colour ?? '#8d8574';
          ctx.strokeStyle = withAlpha(colour, 0.55);
          ctx.setLineDash([6, 5]);
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.ellipse(p.x, p.y, t.radius * TILE_W * 0.5 * camera.zoom, t.radius * TILE_H * 0.5 * camera.zoom, 0, 0, Math.PI * 2);
          ctx.stroke();
          ctx.setLineDash([]);

          // Control marker
          ctx.fillStyle = withAlpha(colour, 0.85);
          ctx.beginPath();
          ctx.moveTo(p.x, p.y - 22 * camera.zoom);
          ctx.lineTo(p.x + 9 * camera.zoom, p.y - 14 * camera.zoom);
          ctx.lineTo(p.x, p.y - 6 * camera.zoom);
          ctx.lineTo(p.x - 9 * camera.zoom, p.y - 14 * camera.zoom);
          ctx.closePath();
          ctx.fill();

          if (camera.zoom > 0.55) {
            ctx.fillStyle = UI.textDim;
            ctx.font = `${Math.round(11 * Math.min(1.4, camera.zoom))}px system-ui, sans-serif`;
            ctx.textAlign = 'center';
            ctx.fillText(t.name, p.x, p.y + 16 * camera.zoom);
          }
        },
      });
    }
  }

  private collectBases(out: Drawable[], input: SceneInput): void {
    const { state, camera, time, selectedBaseId } = input;
    for (const base of state.bases) {
      if (!camera.isVisible(base.wx, base.wy, 400)) continue;
      const owner = state.players.find((p) => p.id === base.owner);
      const colour = owner?.colour ?? '#8d8574';
      out.push({
        depth: base.wx + base.wy,
        draw: () => {
          const ctx = getCtx(input);
          drawBase(ctx, camera, base, colour, time, base.id === selectedBaseId);
          drawBaseLabel(ctx, camera, base, colour);
        },
      });
    }
  }

  private collectUnits(out: Drawable[], input: SceneInput): void {
    const { state, camera, selected, viewer, time } = input;
    for (const unit of state.units) {
      if (!camera.isVisible(unit.x, unit.y, 120)) continue;
      const def = UNIT_BY_ID[unit.defId];
      if (!def) continue;

      // Enemy stealth units are simply not drawn unless the viewer can detect them.
      const owner = state.players.find((p) => p.id === unit.owner);
      if (!owner) continue;
      const viewerPlayer = state.players.find((p) => p.id === viewer);
      const friendly = viewerPlayer ? owner.team === viewerPlayer.team : false;
      if (!friendly && def.specialisations.includes('stealth') && !canSee(state, viewer, unit)) {
        continue;
      }

      const p = camera.worldToScreen(unit.x, unit.y);
      const scale = TILE_W * camera.zoom * 0.5;
      const isSelected = selected.has(unit.id);
      out.push({
        depth: unit.x + unit.y + 0.1,
        draw: () => {
          const ctx = getCtx(input);
          if (isSelected) drawSelectionRing(ctx, p.x, p.y, scale, UI.selection);
          drawUnit(
            { ctx, x: p.x, y: p.y, scale, facing: unit.facing, colour: owner.colour, health: unit.hp / unit.maxHp, time },
            def,
          );
          if (unit.fuel <= 0 && camera.zoom > 0.4) {
            ctx.fillStyle = UI.bad;
            ctx.font = `${Math.round(10 * camera.zoom)}px system-ui, sans-serif`;
            ctx.textAlign = 'center';
            ctx.fillText('no fuel', p.x, p.y - scale * 1.9);
          }
        },
      });
    }
  }

  /** Tracers, muzzle flashes and explosions. */
  private drawEffects(ctx: CanvasRenderingContext2D, input: SceneInput): void {
    const { state, camera } = input;
    for (const e of state.effects) {
      const p = camera.worldToScreen(e.x, e.y);
      if (e.kind === 'shot' && e.tx !== undefined && e.ty !== undefined) {
        const q = camera.worldToScreen(e.tx, e.ty);
        ctx.strokeStyle = withAlpha(e.colour, Math.min(1, e.life * 5));
        ctx.lineWidth = Math.max(1, 2 * camera.zoom);
        ctx.beginPath();
        ctx.moveTo(p.x, p.y - 8 * camera.zoom);
        ctx.lineTo(q.x, q.y - 8 * camera.zoom);
        ctx.stroke();
      } else if (e.kind === 'explosion') {
        const t = 1 - e.life / 0.5;
        const r = (8 + t * 26) * camera.zoom * e.scale;
        ctx.fillStyle = withAlpha(e.colour, (1 - t) * 0.8);
        ctx.beginPath();
        ctx.arc(p.x, p.y - 6 * camera.zoom, r, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = withAlpha('#ffe9b0', (1 - t) * 0.6);
        ctx.beginPath();
        ctx.arc(p.x, p.y - 6 * camera.zoom, r * 0.45, 0, Math.PI * 2);
        ctx.fill();
      } else if (e.kind === 'claim') {
        ctx.strokeStyle = withAlpha(e.colour, 0.5);
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.ellipse(p.x, p.y, 26 * camera.zoom, 13 * camera.zoom, 0, 0, Math.PI * 2);
        ctx.stroke();
      }
    }
  }

  private drawMarquee(ctx: CanvasRenderingContext2D, input: SceneInput): void {
    const m = input.marquee;
    if (!m) return;
    const x = Math.min(m.x0, m.x1);
    const y = Math.min(m.y0, m.y1);
    const w = Math.abs(m.x1 - m.x0);
    const h = Math.abs(m.y1 - m.y0);
    ctx.fillStyle = withAlpha(UI.selection, 0.12);
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = UI.selection;
    ctx.lineWidth = 1.5;
    ctx.strokeRect(x, y, w, h);
  }

  private drawHover(ctx: CanvasRenderingContext2D, input: SceneInput): void {
    const { hover, camera } = input;
    if (!hover || camera.zoom < 0.3) return;
    const p = camera.worldToScreen(Math.floor(hover.x), Math.floor(hover.y));
    const tw = TILE_W * camera.zoom;
    const th = TILE_H * camera.zoom;
    ctx.strokeStyle = withAlpha(UI.accent, 0.7);
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
    ctx.lineTo(p.x + tw / 2, p.y + th / 2);
    ctx.lineTo(p.x, p.y + th);
    ctx.lineTo(p.x - tw / 2, p.y + th / 2);
    ctx.closePath();
    ctx.stroke();
  }

  invalidateTerrain(): void {
    this.terrain.invalidate();
  }
}

/** The renderer draws into a single context; this keeps closures tidy. */
let activeCtx: CanvasRenderingContext2D | null = null;
function getCtx(_input: SceneInput): CanvasRenderingContext2D {
  if (!activeCtx) throw new Error('Renderer context not bound.');
  return activeCtx;
}

export function bindContext(ctx: CanvasRenderingContext2D): void {
  activeCtx = ctx;
}

/** Base name plate. */
function drawBaseLabel(
  ctx: CanvasRenderingContext2D,
  camera: Camera,
  base: Base,
  colour: string,
): void {
  if (camera.zoom < 0.4) return;
  const p = camera.worldToScreen(base.wx, base.wy);
  const size = Math.round(12 * Math.min(1.3, camera.zoom));
  ctx.font = `600 ${size}px system-ui, sans-serif`;
  ctx.textAlign = 'center';
  const label = base.name;
  const w = ctx.measureText(label).width + 12;
  const y = p.y - 70 * camera.zoom;
  ctx.fillStyle = 'rgba(12, 16, 20, 0.75)';
  ctx.fillRect(p.x - w / 2, y - size, w, size + 8);
  ctx.fillStyle = colour;
  ctx.fillRect(p.x - w / 2, y - size, 3, size + 8);
  ctx.fillStyle = UI.text;
  ctx.fillText(label, p.x, y);
}

/** Desert props. Drawn as simple, readable silhouettes. */
function drawDecoration(camera: Camera, x: number, y: number, dec: Decoration): void {
  const ctx = activeCtx;
  if (!ctx) return;
  const s = TILE_W * camera.zoom * 0.35 * dec.scale;

  ctx.fillStyle = 'rgba(0,0,0,0.22)';
  ctx.beginPath();
  ctx.ellipse(x, y, s * 0.5, s * 0.24, 0, 0, Math.PI * 2);
  ctx.fill();

  switch (dec.kind) {
    case 'palm': {
      ctx.strokeStyle = '#6f5b3e';
      ctx.lineWidth = Math.max(1, s * 0.13);
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.quadraticCurveTo(x + s * 0.14, y - s * 0.9, x + s * 0.22, y - s * 1.5);
      ctx.stroke();
      ctx.fillStyle = '#5f7a3d';
      for (let i = 0; i < 6; i++) {
        const a = (i / 6) * Math.PI * 2 + dec.variant;
        ctx.beginPath();
        ctx.ellipse(
          x + s * 0.22 + Math.cos(a) * s * 0.5,
          y - s * 1.5 + Math.sin(a) * s * 0.25,
          s * 0.42, s * 0.13, a, 0, Math.PI * 2,
        );
        ctx.fill();
      }
      break;
    }
    case 'cactus': {
      ctx.fillStyle = '#5c7a4a';
      ctx.fillRect(x - s * 0.11, y - s * 0.95, s * 0.22, s * 0.95);
      ctx.fillRect(x - s * 0.4, y - s * 0.62, s * 0.3, s * 0.13);
      ctx.fillRect(x - s * 0.4, y - s * 0.75, s * 0.13, s * 0.26);
      break;
    }
    case 'rock': {
      ctx.fillStyle = dec.variant % 2 === 0 ? '#8a7c66' : '#7a6e5a';
      ctx.beginPath();
      ctx.moveTo(x - s * 0.45, y);
      ctx.lineTo(x - s * 0.2, y - s * 0.55);
      ctx.lineTo(x + s * 0.25, y - s * 0.45);
      ctx.lineTo(x + s * 0.48, y);
      ctx.closePath();
      ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,0.1)';
      ctx.beginPath();
      ctx.moveTo(x - s * 0.2, y - s * 0.55);
      ctx.lineTo(x + s * 0.25, y - s * 0.45);
      ctx.lineTo(x + s * 0.1, y - s * 0.2);
      ctx.closePath();
      ctx.fill();
      break;
    }
    case 'wreck': {
      ctx.fillStyle = '#6b6257';
      ctx.fillRect(x - s * 0.4, y - s * 0.3, s * 0.8, s * 0.3);
      ctx.fillStyle = '#4d463d';
      ctx.beginPath();
      ctx.arc(x - s * 0.22, y, s * 0.14, 0, Math.PI * 2);
      ctx.arc(x + s * 0.22, y, s * 0.14, 0, Math.PI * 2);
      ctx.fill();
      break;
    }
    case 'ruin': {
      ctx.fillStyle = '#9c9077';
      ctx.fillRect(x - s * 0.42, y - s * 0.5, s * 0.18, s * 0.5);
      ctx.fillRect(x + s * 0.1, y - s * 0.7, s * 0.2, s * 0.7);
      ctx.fillRect(x - s * 0.42, y - s * 0.14, s * 0.84, s * 0.14);
      break;
    }
  }
}

/** Exposed so the app can draw the unit at an arbitrary spot, e.g. in the build menu. */
export function drawUnitPreview(
  ctx: CanvasRenderingContext2D,
  defId: string,
  x: number,
  y: number,
  scale: number,
  colour: string,
  time: number,
): void {
  const def = UNIT_BY_ID[defId];
  if (!def) return;
  drawUnit({ ctx, x, y, scale, facing: -0.6, colour, health: 1, time }, def);
}
