import type { Base, BuildingInstance } from '../core/state';
import { BUILDING_BY_ID } from '../data/buildings';
import { BASE_TYPE_BY_KIND } from '../data/bases';
import { STRUCTURE_STYLES, mix, withAlpha } from './palette';
import type { Camera } from './camera';
import { TILE_W, TILE_H } from '../data/config';

/**
 * Base and building artwork.
 *
 * A base is drawn as a walled desert compound: a graded platform, a perimeter wall
 * with corner towers, and individual structures placed on the base's build grid.
 * Everything is drawn procedurally so a level-1 shack and a level-20 complex are
 * visibly different buildings without needing dozens of hand-made sprites.
 */

/** Converts a base-local grid cell to a world tile coordinate. */
export function cellToWorld(base: Base, cx: number, cy: number): { x: number; y: number } {
  const grid = BASE_TYPE_BY_KIND[base.kind].grid;
  return {
    x: base.wx - grid.w / 2 + cx,
    y: base.wy - grid.h / 2 + cy,
  };
}

export function drawBase(
  ctx: CanvasRenderingContext2D,
  camera: Camera,
  base: Base,
  playerColour: string,
  time: number,
  selected: boolean,
): void {
  const grid = BASE_TYPE_BY_KIND[base.kind].grid;
  const accent = BASE_TYPE_BY_KIND[base.kind].accent;
  const tw = TILE_W * camera.zoom;
  const th = TILE_H * camera.zoom;

  drawPlatform(ctx, camera, base, grid, accent, selected, playerColour);

  // Structures are sorted back-to-front by grid depth so overlaps are correct.
  const sorted = [...base.buildings].sort((a, b) => a.x + a.y - (b.x + b.y));
  for (const b of sorted) {
    const def = BUILDING_BY_ID[b.defId];
    if (!def) continue;
    const centre = cellToWorld(base, b.x + def.size.w / 2 - 0.5, b.y + def.size.h / 2 - 0.5);
    const p = camera.worldToScreen(centre.x, centre.y);
    drawStructure(ctx, b, p.x, p.y, tw, th, playerColour, time);
  }

  drawPerimeter(ctx, camera, base, grid, playerColour);
}

/** Graded sand platform the compound sits on, plus a faction-coloured edge. */
function drawPlatform(
  ctx: CanvasRenderingContext2D,
  camera: Camera,
  base: Base,
  grid: { w: number; h: number },
  accent: string,
  selected: boolean,
  playerColour: string,
): void {
  const corners = [
    cellToWorld(base, -0.5, -0.5),
    cellToWorld(base, grid.w - 0.5, -0.5),
    cellToWorld(base, grid.w - 0.5, grid.h - 0.5),
    cellToWorld(base, -0.5, grid.h - 0.5),
  ].map((c) => camera.worldToScreen(c.x, c.y));

  ctx.fillStyle = mix(accent, '#4a4034', 0.62);
  ctx.beginPath();
  ctx.moveTo(corners[0].x, corners[0].y);
  for (let i = 1; i < 4; i++) ctx.lineTo(corners[i].x, corners[i].y);
  ctx.closePath();
  ctx.fill();

  ctx.strokeStyle = selected ? '#8fe3a4' : withAlpha(playerColour, 0.75);
  ctx.lineWidth = selected ? 3 : 2;
  ctx.stroke();
}

/** Perimeter wall with corner towers, scaled by the base's wall level. */
function drawPerimeter(
  ctx: CanvasRenderingContext2D,
  camera: Camera,
  base: Base,
  grid: { w: number; h: number },
  playerColour: string,
): void {
  const wall = base.buildings.find((b) => b.defId === 'wall');
  const level = wall?.level ?? 0;
  if (level < 1 || camera.zoom < 0.3) return;

  const height = (6 + level * 1.2) * camera.zoom;
  const corners: Array<{ x: number; y: number }> = [
    cellToWorld(base, -0.5, -0.5),
    cellToWorld(base, grid.w - 0.5, -0.5),
    cellToWorld(base, grid.w - 0.5, grid.h - 0.5),
    cellToWorld(base, -0.5, grid.h - 0.5),
  ];

  ctx.strokeStyle = '#8d8574';
  ctx.lineWidth = Math.max(2, 3 * camera.zoom);
  ctx.beginPath();
  for (let i = 0; i < 4; i++) {
    const p = camera.worldToScreen(corners[i].x, corners[i].y);
    if (i === 0) ctx.moveTo(p.x, p.y - height);
    else ctx.lineTo(p.x, p.y - height);
  }
  ctx.closePath();
  ctx.stroke();

  for (const corner of corners) {
    const p = camera.worldToScreen(corner.x, corner.y);
    const w = 5 * camera.zoom;
    ctx.fillStyle = '#9c9583';
    ctx.fillRect(p.x - w, p.y - height - w * 1.6, w * 2, height + w * 1.6);
    ctx.fillStyle = withAlpha(playerColour, 0.9);
    ctx.fillRect(p.x - w, p.y - height - w * 1.9, w * 2, w * 0.5);
  }
}

/** One building, drawn as an isometric block with role-specific detailing. */
function drawStructure(
  ctx: CanvasRenderingContext2D,
  instance: BuildingInstance,
  x: number,
  y: number,
  tw: number,
  th: number,
  playerColour: string,
  time: number,
): void {
  const def = BUILDING_BY_ID[instance.defId];
  if (!def) return;
  const style = STRUCTURE_STYLES[def.kind] ?? STRUCTURE_STYLES.command;
  const level = Math.max(1, instance.level);

  const w = (def.size.w * tw) / 2.4;
  const d = (def.size.h * th) / 1.2;
  // Buildings physically grow with level, so investment is visible on the map.
  const h = (10 + Math.min(level, 20) * 2.6) * (tw / TILE_W);

  const under = instance.level < 1 || instance.upgrading !== null;
  const disabled = instance.disabled;

  ctx.save();
  ctx.globalAlpha = disabled ? 0.4 : under ? 0.75 : 1;

  // Base slab
  ctx.fillStyle = mix(style.wall, '#000000', 0.45);
  drawDiamond(ctx, x, y, w * 1.12, d * 1.12);

  // Walls: left in shadow, right lit.
  ctx.fillStyle = mix(style.wall, '#000000', 0.32);
  ctx.beginPath();
  ctx.moveTo(x - w, y);
  ctx.lineTo(x, y + d / 2);
  ctx.lineTo(x, y + d / 2 - h);
  ctx.lineTo(x - w, y - h);
  ctx.closePath();
  ctx.fill();

  ctx.fillStyle = style.wall;
  ctx.beginPath();
  ctx.moveTo(x + w, y);
  ctx.lineTo(x, y + d / 2);
  ctx.lineTo(x, y + d / 2 - h);
  ctx.lineTo(x + w, y - h);
  ctx.closePath();
  ctx.fill();

  // Roof
  ctx.fillStyle = disabled ? '#3a3a36' : style.roof;
  drawDiamond(ctx, x, y - h, w, d);

  // Faction stripe along the roof edge.
  ctx.strokeStyle = withAlpha(playerColour, 0.85);
  ctx.lineWidth = Math.max(1, tw * 0.03);
  ctx.beginPath();
  ctx.moveTo(x - w, y - h);
  ctx.lineTo(x, y + d / 2 - h);
  ctx.stroke();

  drawDetails(ctx, def.kind, x, y - h, w, d, h, style.trim, level, time, tw);

  // Construction scaffold and progress ring.
  if (under && instance.upgrading) {
    const progress = 1 - instance.upgrading.remaining / Math.max(1, instance.upgrading.remaining + 1);
    drawScaffold(ctx, x, y, w, d, h, progress);
  }

  ctx.restore();
}

function drawDiamond(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, d: number): void {
  ctx.beginPath();
  ctx.moveTo(x, y - d / 2);
  ctx.lineTo(x + w, y);
  ctx.lineTo(x, y + d / 2);
  ctx.lineTo(x - w, y);
  ctx.closePath();
  ctx.fill();
}

/** Role-specific rooftop details: chimneys, dishes, derricks, gun barrels. */
function drawDetails(
  ctx: CanvasRenderingContext2D,
  kind: string,
  x: number,
  roofY: number,
  w: number,
  d: number,
  h: number,
  trim: string,
  level: number,
  time: number,
  tw: number,
): void {
  ctx.fillStyle = trim;
  ctx.strokeStyle = trim;
  ctx.lineWidth = Math.max(1, tw * 0.03);
  const unit = tw / TILE_W;

  switch (kind) {
    case 'power': {
      // Cooling stacks with drifting smoke.
      const stacks = Math.min(4, 1 + Math.floor(level / 6));
      for (let i = 0; i < stacks; i++) {
        const sx = x - w * 0.4 + (i * w * 0.8) / Math.max(1, stacks - 1 || 1);
        ctx.fillRect(sx - 2 * unit, roofY - 14 * unit, 4 * unit, 14 * unit);
        ctx.globalAlpha *= 0.35;
        ctx.beginPath();
        ctx.ellipse(sx + Math.sin(time + i) * 3 * unit, roofY - 22 * unit, 5 * unit, 3.5 * unit, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalAlpha /= 0.35;
      }
      break;
    }
    case 'radar': {
      // Rotating dish.
      const a = time * 0.9;
      ctx.save();
      ctx.translate(x, roofY - 10 * unit);
      ctx.scale(1, 0.5);
      ctx.rotate(a);
      ctx.beginPath();
      ctx.arc(0, 0, 9 * unit, Math.PI * 0.15, Math.PI * 0.85);
      ctx.lineTo(0, 0);
      ctx.closePath();
      ctx.fill();
      ctx.restore();
      ctx.beginPath();
      ctx.moveTo(x, roofY);
      ctx.lineTo(x, roofY - 10 * unit);
      ctx.stroke();
      break;
    }
    case 'fuel_pump': {
      // Nodding-donkey derrick.
      const swing = Math.sin(time * 1.6) * 0.35;
      ctx.beginPath();
      ctx.moveTo(x - w * 0.3, roofY);
      ctx.lineTo(x, roofY - 16 * unit);
      ctx.lineTo(x + w * 0.3, roofY);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(x - w * 0.35, roofY - 16 * unit + swing * 8 * unit);
      ctx.lineTo(x + w * 0.35, roofY - 16 * unit - swing * 8 * unit);
      ctx.stroke();
      break;
    }
    case 'steel_mine':
    case 'aluminum_mine': {
      // Headframe with a conveyor.
      ctx.beginPath();
      ctx.moveTo(x - w * 0.4, roofY);
      ctx.lineTo(x - w * 0.1, roofY - 15 * unit);
      ctx.lineTo(x + w * 0.2, roofY - 15 * unit);
      ctx.lineTo(x + w * 0.45, roofY + d * 0.15);
      ctx.stroke();
      break;
    }
    case 'defense_tower': {
      // Turret with a barrel that tracks slowly.
      const a = time * 0.5;
      ctx.beginPath();
      ctx.ellipse(x, roofY - 6 * unit, 7 * unit, 4 * unit, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.lineWidth = Math.max(1.5, tw * 0.045);
      ctx.beginPath();
      ctx.moveTo(x, roofY - 8 * unit);
      ctx.lineTo(x + Math.cos(a) * 16 * unit, roofY - 8 * unit + Math.sin(a) * 8 * unit);
      ctx.stroke();
      break;
    }
    case 'research': {
      // Antenna array with a pulsing indicator.
      for (let i = -1; i <= 1; i++) {
        ctx.beginPath();
        ctx.moveTo(x + i * w * 0.35, roofY);
        ctx.lineTo(x + i * w * 0.35, roofY - (12 + i * 2) * unit);
        ctx.stroke();
      }
      ctx.globalAlpha *= 0.4 + 0.6 * Math.abs(Math.sin(time * 2));
      ctx.beginPath();
      ctx.arc(x, roofY - 14 * unit, 3 * unit, 0, Math.PI * 2);
      ctx.fill();
      break;
    }
    case 'factory': {
      // Sawtooth roof lights.
      for (let i = -1; i <= 1; i++) {
        ctx.beginPath();
        ctx.moveTo(x + i * w * 0.4, roofY + d * 0.1);
        ctx.lineTo(x + i * w * 0.4 + w * 0.18, roofY - 6 * unit);
        ctx.stroke();
      }
      break;
    }
    case 'command': {
      // Flag mast.
      ctx.beginPath();
      ctx.moveTo(x, roofY);
      ctx.lineTo(x, roofY - 20 * unit);
      ctx.stroke();
      const wave = Math.sin(time * 3) * 2 * unit;
      ctx.beginPath();
      ctx.moveTo(x, roofY - 20 * unit);
      ctx.lineTo(x + 12 * unit, roofY - 17 * unit + wave);
      ctx.lineTo(x, roofY - 13 * unit);
      ctx.closePath();
      ctx.fill();
      break;
    }
    case 'steel_depot':
    case 'aluminum_depot':
    case 'fuel_depot': {
      // Storage tanks.
      for (let i = -1; i <= 1; i += 2) {
        ctx.beginPath();
        ctx.ellipse(x + i * w * 0.35, roofY - 5 * unit, 6 * unit, 3.5 * unit, 0, 0, Math.PI * 2);
        ctx.fill();
      }
      break;
    }
    default:
      break;
  }
  void h;
}

/** Scaffolding shown while a structure is under construction or upgrading. */
function drawScaffold(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  d: number,
  h: number,
  progress: number,
): void {
  ctx.strokeStyle = 'rgba(240, 200, 110, 0.85)';
  ctx.setLineDash([4, 3]);
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(x - w, y);
  ctx.lineTo(x - w, y - h);
  ctx.moveTo(x + w, y);
  ctx.lineTo(x + w, y - h);
  ctx.moveTo(x, y + d / 2);
  ctx.lineTo(x, y + d / 2 - h);
  ctx.stroke();
  ctx.setLineDash([]);
  void progress;
}
