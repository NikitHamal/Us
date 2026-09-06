import type { UnitDef } from '../data/types';
import { hullColours, mix, withAlpha } from './palette';

/**
 * Procedural unit artwork.
 *
 * Every vehicle is drawn from primitives at draw time, tinted by faction colour and
 * shaped by the unit's domain and weight class. This keeps the visual language
 * consistent across forty-plus units, costs nothing to load, and is entirely original
 * art rather than anything lifted from another game.
 */

export interface DrawContext {
  ctx: CanvasRenderingContext2D;
  /** Screen position of the unit's ground contact point. */
  x: number;
  y: number;
  /** Pixels per tile, so art scales with zoom. */
  scale: number;
  facing: number;
  colour: string;
  /** 0..1 health, drives scorching. */
  health: number;
  time: number;
}

/** Weight class inferred from slot cost, matching the data layer's own tiering. */
function sizeOf(def: UnitDef): number {
  return 0.5 + def.slots * 0.18;
}

export function drawUnit(d: DrawContext, def: UnitDef): void {
  const { ctx } = d;
  const s = d.scale * sizeOf(def);
  const c = hullColours(d.colour);

  ctx.save();
  ctx.translate(d.x, d.y);

  // Contact shadow grounds the unit against the terrain.
  ctx.fillStyle = 'rgba(0, 0, 0, 0.28)';
  ctx.beginPath();
  ctx.ellipse(0, 0, s * 0.62, s * 0.3, 0, 0, Math.PI * 2);
  ctx.fill();

  switch (def.domain) {
    case 'air':
      def.producedAt === 'helicopter' ? drawHelicopter(d, def, s, c) : drawPlane(d, def, s, c);
      break;
    case 'naval':
      drawShip(d, def, s, c);
      break;
    case 'rail':
      drawRailcar(d, def, s, c);
      break;
    default:
      drawTank(d, def, s, c);
  }

  if (d.health < 0.99) drawHealthBar(ctx, s, d.health);
  ctx.restore();
}

type Hull = ReturnType<typeof hullColours>;

/** Isometric box helper: draws a cuboid with a top face and two lit side faces. */
function isoBox(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  w: number,
  d: number,
  h: number,
  c: Hull,
  rot: number,
): void {
  const cos = Math.cos(rot);
  const sin = Math.sin(rot);
  // Project the four base corners of the box, squashed vertically for the iso view.
  const pts = [
    [-w, -d], [w, -d], [w, d], [-w, d],
  ].map(([px, py]) => {
    const rx = px * cos - py * sin;
    const ry = px * sin + py * cos;
    return { x: cx + rx, y: cy + ry * 0.5 };
  });

  // Sides: draw the two facing away from the light first.
  for (let i = 0; i < 4; i++) {
    const a = pts[i];
    const b = pts[(i + 1) % 4];
    const facingLight = (a.x + b.x) / 2 > cx;
    ctx.fillStyle = facingLight ? c.hull : c.dark;
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.lineTo(b.x, b.y - h);
    ctx.lineTo(a.x, a.y - h);
    ctx.closePath();
    ctx.fill();
  }

  ctx.fillStyle = c.light;
  ctx.beginPath();
  ctx.moveTo(pts[0].x, pts[0].y - h);
  for (let i = 1; i < 4; i++) ctx.lineTo(pts[i].x, pts[i].y - h);
  ctx.closePath();
  ctx.fill();
}

function drawTank(d: DrawContext, def: UnitDef, s: number, c: Hull): void {
  const { ctx, facing } = d;
  const heavy = def.slots >= 3;

  // Tracks
  ctx.fillStyle = c.dark;
  isoBox(ctx, 0, -s * 0.06, s * 0.5, s * 0.32, s * 0.14, { ...c, hull: '#3b3d34', light: '#4a4c40', dark: '#2a2c25' }, facing);
  // Hull
  isoBox(ctx, 0, -s * 0.16, s * 0.44, s * 0.26, s * 0.2, c, facing);
  // Turret
  const turretY = -s * 0.34;
  isoBox(ctx, 0, turretY, s * 0.24, s * 0.18, s * 0.16, { ...c, light: mix(c.light, '#ffffff', 0.1) }, facing);

  // Barrel, pointing along facing.
  if (def.damage > 0) {
    const len = s * (heavy ? 0.72 : 0.55) * (def.range > 10 ? 1.15 : 1);
    ctx.strokeStyle = c.dark;
    ctx.lineWidth = Math.max(1, s * (heavy ? 0.09 : 0.06));
    ctx.beginPath();
    ctx.moveTo(0, turretY - s * 0.08);
    ctx.lineTo(Math.cos(facing) * len, turretY - s * 0.08 + Math.sin(facing) * len * 0.5);
    ctx.stroke();
  }

  // Anti-air units get a visibly elevated mount so they read differently at a glance.
  if (def.specialisations.some((x) => x === 'vs_air' || x === 'vs_aircraft')) {
    ctx.strokeStyle = c.light;
    ctx.lineWidth = Math.max(1, s * 0.05);
    ctx.beginPath();
    ctx.moveTo(0, turretY - s * 0.1);
    ctx.lineTo(Math.cos(facing) * s * 0.4, turretY - s * 0.5);
    ctx.stroke();
  }
}

function drawPlane(d: DrawContext, def: UnitDef, s: number, c: Hull): void {
  const { ctx, facing, time } = d;
  // Aircraft fly, so they are drawn lifted with a separate shadow already laid down.
  const altitude = s * 1.5 + Math.sin(time * 2 + def.name.length) * s * 0.06;
  ctx.save();
  ctx.translate(0, -altitude);
  ctx.rotate(facing);
  ctx.scale(1, 0.55);

  const span = s * (def.slots >= 3 ? 1.5 : 1.1);
  // Wings
  ctx.fillStyle = c.hull;
  ctx.beginPath();
  ctx.moveTo(-s * 0.1, 0);
  ctx.lineTo(-s * 0.3, -span * 0.5);
  ctx.lineTo(s * 0.05, -span * 0.5);
  ctx.lineTo(s * 0.2, 0);
  ctx.lineTo(s * 0.05, span * 0.5);
  ctx.lineTo(-s * 0.3, span * 0.5);
  ctx.closePath();
  ctx.fill();

  // Fuselage
  ctx.fillStyle = c.light;
  ctx.beginPath();
  ctx.ellipse(0, 0, s * 0.62, s * 0.14, 0, 0, Math.PI * 2);
  ctx.fill();

  // Tailplane
  ctx.fillStyle = c.dark;
  ctx.beginPath();
  ctx.moveTo(-s * 0.5, 0);
  ctx.lineTo(-s * 0.7, -s * 0.28);
  ctx.lineTo(-s * 0.42, -s * 0.02);
  ctx.closePath();
  ctx.fill();
  ctx.beginPath();
  ctx.moveTo(-s * 0.5, 0);
  ctx.lineTo(-s * 0.7, s * 0.28);
  ctx.lineTo(-s * 0.42, s * 0.02);
  ctx.closePath();
  ctx.fill();

  // Propeller blur, or jet glow for the ME262.
  if (def.id === 'me262') {
    ctx.fillStyle = withAlpha('#ffb347', 0.55);
    ctx.beginPath();
    ctx.ellipse(-s * 0.6, 0, s * 0.2, s * 0.08, 0, 0, Math.PI * 2);
    ctx.fill();
  } else {
    ctx.strokeStyle = withAlpha('#dfe6ea', 0.4);
    ctx.lineWidth = Math.max(1, s * 0.04);
    ctx.beginPath();
    ctx.ellipse(s * 0.6, 0, s * 0.06, s * 0.42, 0, 0, Math.PI * 2);
    ctx.stroke();
  }
  ctx.restore();
}

function drawHelicopter(d: DrawContext, def: UnitDef, s: number, c: Hull): void {
  const { ctx, facing, time } = d;
  const altitude = s * 1.0 + Math.sin(time * 3 + def.name.length) * s * 0.05;
  ctx.save();
  ctx.translate(0, -altitude);

  // Body
  isoBox(ctx, 0, 0, s * 0.34, s * 0.2, s * 0.24, c, facing);
  // Tail boom
  ctx.strokeStyle = c.dark;
  ctx.lineWidth = Math.max(1, s * 0.07);
  ctx.beginPath();
  ctx.moveTo(0, -s * 0.14);
  ctx.lineTo(-Math.cos(facing) * s * 0.8, -s * 0.14 - Math.sin(facing) * s * 0.4);
  ctx.stroke();

  // Rotor disc, animated.
  const spin = time * 22;
  ctx.strokeStyle = withAlpha('#e6ecef', 0.35);
  ctx.lineWidth = Math.max(1, s * 0.05);
  ctx.beginPath();
  ctx.ellipse(0, -s * 0.34, s * 0.95, s * 0.34, 0, 0, Math.PI * 2);
  ctx.stroke();
  ctx.strokeStyle = withAlpha('#f4f7f8', 0.75);
  for (let i = 0; i < 2; i++) {
    const a = spin + (i * Math.PI) / 2;
    ctx.beginPath();
    ctx.moveTo(-Math.cos(a) * s * 0.95, -s * 0.34 - Math.sin(a) * s * 0.34);
    ctx.lineTo(Math.cos(a) * s * 0.95, -s * 0.34 + Math.sin(a) * s * 0.34);
    ctx.stroke();
  }
  ctx.restore();
}

function drawShip(d: DrawContext, def: UnitDef, s: number, c: Hull): void {
  const { ctx, facing, time } = d;
  const bob = Math.sin(time * 1.6 + def.name.length) * s * 0.04;
  ctx.save();
  ctx.translate(0, bob);

  // Wake
  ctx.fillStyle = 'rgba(210, 240, 250, 0.18)';
  ctx.beginPath();
  ctx.ellipse(-Math.cos(facing) * s * 0.7, -Math.sin(facing) * s * 0.35, s * 0.7, s * 0.24, facing, 0, Math.PI * 2);
  ctx.fill();

  ctx.save();
  ctx.rotate(facing);
  ctx.scale(1, 0.5);

  // Hull: a pointed prow reads instantly as a ship.
  const len = s * (def.slots >= 3 ? 1.5 : 1.05);
  ctx.fillStyle = c.hull;
  ctx.beginPath();
  ctx.moveTo(len * 0.55, 0);
  ctx.lineTo(len * 0.15, -s * 0.32);
  ctx.lineTo(-len * 0.5, -s * 0.28);
  ctx.lineTo(-len * 0.5, s * 0.28);
  ctx.lineTo(len * 0.15, s * 0.32);
  ctx.closePath();
  ctx.fill();

  // Deck
  ctx.fillStyle = c.light;
  ctx.beginPath();
  ctx.ellipse(0, 0, len * 0.34, s * 0.18, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();

  // Superstructure, drawn upright so it stands above the deck.
  if (def.id !== 'm_class_sub') {
    isoBox(ctx, 0, -s * 0.05, s * 0.18, s * 0.14, s * 0.3, c, facing);
    if (def.damage > 0) {
      ctx.strokeStyle = c.dark;
      ctx.lineWidth = Math.max(1, s * 0.06);
      ctx.beginPath();
      ctx.moveTo(0, -s * 0.3);
      ctx.lineTo(Math.cos(facing) * s * 0.6, -s * 0.3 + Math.sin(facing) * s * 0.3);
      ctx.stroke();
    }
  } else {
    // Submarine: low conning tower only.
    isoBox(ctx, 0, -s * 0.05, s * 0.1, s * 0.08, s * 0.22, c, facing);
  }
  ctx.restore();
}

function drawRailcar(d: DrawContext, def: UnitDef, s: number, c: Hull): void {
  const { ctx, facing } = d;
  const isEngine = def.id === 'locomotive';

  // Bogies
  isoBox(ctx, 0, -s * 0.04, s * 0.55, s * 0.16, s * 0.08,
    { hull: '#33352d', light: '#41443a', dark: '#232620' }, facing);
  // Car body
  isoBox(ctx, 0, -s * 0.12, s * 0.6, s * 0.2, s * 0.26, c, facing);

  if (isEngine) {
    // Boiler and stack.
    isoBox(ctx, Math.cos(facing) * s * 0.22, -s * 0.32 + Math.sin(facing) * s * 0.11,
      s * 0.2, s * 0.16, s * 0.2, { ...c, light: mix(c.light, '#000', 0.15) }, facing);
    ctx.fillStyle = 'rgba(200, 200, 200, 0.28)';
    ctx.beginPath();
    ctx.ellipse(Math.cos(facing) * s * 0.3, -s * 0.66, s * 0.16, s * 0.1, 0, 0, Math.PI * 2);
    ctx.fill();
  } else if (def.damage > 0) {
    // Gun mount on the flatcar.
    isoBox(ctx, 0, -s * 0.36, s * 0.18, s * 0.14, s * 0.14, c, facing);
    ctx.strokeStyle = c.dark;
    ctx.lineWidth = Math.max(1, s * 0.07);
    ctx.beginPath();
    ctx.moveTo(0, -s * 0.46);
    const elev = def.specialisations.includes('vs_air') ? -s * 0.55 : -s * 0.5;
    ctx.lineTo(Math.cos(facing) * s * 0.75, elev + Math.sin(facing) * s * 0.3);
    ctx.stroke();
  }
}

function drawHealthBar(ctx: CanvasRenderingContext2D, s: number, health: number): void {
  const w = s * 1.1;
  const h = Math.max(2, s * 0.1);
  const y = -s * 1.5;
  ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
  ctx.fillRect(-w / 2, y, w, h);
  ctx.fillStyle = health > 0.5 ? '#7fd98d' : health > 0.25 ? '#e8c65f' : '#e8695f';
  ctx.fillRect(-w / 2, y, w * health, h);
}

/** Selection ring drawn beneath a selected unit. */
export function drawSelectionRing(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  scale: number,
  colour: string,
): void {
  ctx.strokeStyle = colour;
  ctx.lineWidth = Math.max(1, scale * 0.06);
  ctx.beginPath();
  ctx.ellipse(x, y, scale * 0.65, scale * 0.32, 0, 0, Math.PI * 2);
  ctx.stroke();
}
