import type { WorldMap } from '../core/worldmap';
import { Terrain, idx, isRail } from '../core/worldmap';
import type { Camera } from './camera';
import { TERRAIN_STYLES, WATER_HIGHLIGHT, mix } from './palette';
import { TILE_W, TILE_H } from '../data/config';

/**
 * Terrain rendering.
 *
 * Drawing 262144 diamonds every frame is not viable, so the world is split into
 * chunks that are rasterised once into offscreen canvases and then blitted. Only
 * chunks intersecting the viewport are ever built, and they are cached until the
 * zoom level changes enough to warrant a re-render.
 */

const CHUNK = 32;

interface Chunk {
  canvas: HTMLCanvasElement;
  /** Screen offset of the chunk's bounding box relative to its world origin. */
  offsetX: number;
  offsetY: number;
  zoom: number;
}

export class TerrainRenderer {
  private readonly cache = new Map<string, Chunk>();
  private readonly map: WorldMap;
  /** Chunks are rasterised at a quantised zoom to avoid rebuilding on every wheel tick. */
  private renderZoom = 1;

  constructor(map: WorldMap) {
    this.map = map;
  }

  /** Quantise zoom into steps so the cache is reused across small zoom changes. */
  private quantiseZoom(zoom: number): number {
    return Math.max(0.15, Math.round(zoom * 4) / 4);
  }

  draw(ctx: CanvasRenderingContext2D, camera: Camera, time: number): void {
    const q = this.quantiseZoom(camera.zoom);
    if (q !== this.renderZoom) {
      this.renderZoom = q;
      this.cache.clear();
    }

    const bounds = camera.visibleBounds(CHUNK);
    const cx0 = Math.floor(bounds.x0 / CHUNK);
    const cy0 = Math.floor(bounds.y0 / CHUNK);
    const cx1 = Math.floor(bounds.x1 / CHUNK);
    const cy1 = Math.floor(bounds.y1 / CHUNK);

    // Chunks are drawn back to front so the isometric overlap is correct.
    for (let sum = cx0 + cy0; sum <= cx1 + cy1; sum++) {
      for (let cy = cy0; cy <= cy1; cy++) {
        const cx = sum - cy;
        if (cx < cx0 || cx > cx1) continue;
        this.drawChunk(ctx, camera, cx, cy);
      }
    }

    this.drawWaterShimmer(ctx, camera, time);
  }

  private drawChunk(ctx: CanvasRenderingContext2D, camera: Camera, cx: number, cy: number): void {
    const key = `${cx},${cy}`;
    let chunk = this.cache.get(key);
    if (!chunk) {
      const built = this.buildChunk(cx, cy);
      if (!built) return;
      chunk = built;
      this.cache.set(key, chunk);
      // Bound memory: a large pan should not retain the whole map.
      if (this.cache.size > 220) {
        const oldest = this.cache.keys().next().value;
        if (oldest !== undefined) this.cache.delete(oldest);
      }
    }
    const origin = camera.worldToScreen(cx * CHUNK, cy * CHUNK);
    ctx.drawImage(chunk.canvas, Math.round(origin.x + chunk.offsetX), Math.round(origin.y + chunk.offsetY));
  }

  /** Rasterises one CHUNK x CHUNK block of tiles into an offscreen canvas. */
  private buildChunk(cx: number, cy: number): Chunk | null {
    const zoom = this.renderZoom;
    const tw = TILE_W * zoom;
    const th = TILE_H * zoom;

    // Bounding box of a CHUNK x CHUNK diamond, plus headroom for tile height.
    const width = Math.ceil((CHUNK + CHUNK) * (tw / 2)) + 4;
    const height = Math.ceil((CHUNK + CHUNK) * (th / 2)) + Math.ceil(th * 6);
    if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return null;

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const c = canvas.getContext('2d');
    if (!c) return null;

    // The leftmost point of the diamond is at world (cx*CHUNK, cy*CHUNK+CHUNK).
    const offsetX = -CHUNK * (tw / 2);
    const offsetY = -Math.ceil(th * 5);

    for (let ty = 0; ty < CHUNK; ty++) {
      for (let tx = 0; tx < CHUNK; tx++) {
        const wx = cx * CHUNK + tx;
        const wy = cy * CHUNK + ty;
        if (wx >= this.map.width || wy >= this.map.height) continue;
        const localX = (tx - ty) * (tw / 2) - offsetX;
        const localY = (tx + ty) * (th / 2) - offsetY;
        this.drawTile(c, this.map, wx, wy, localX, localY, tw, th);
      }
    }

    return { canvas, offsetX, offsetY, zoom };
  }

  /** Draws one tile as a lit diamond with an extruded side wall for elevation. */
  private drawTile(
    c: CanvasRenderingContext2D,
    map: WorldMap,
    wx: number,
    wy: number,
    x: number,
    y: number,
    tw: number,
    th: number,
  ): void {
    const i = idx(wx, wy);
    const terrain = map.terrain[i] as number;
    const style = TERRAIN_STYLES[terrain] ?? TERRAIN_STYLES[Terrain.Sand];
    const h = map.height01[i];

    // Elevation lift: higher ground physically sits higher on screen.
    const lift = Math.max(0, (h - 0.4)) * th * 3.2;
    const top = y - lift;

    // Side walls give the terrain real volume rather than a flat tilemap look.
    if (lift > 0.5) {
      c.fillStyle = style.left;
      c.beginPath();
      c.moveTo(x - tw / 2, top + th / 2);
      c.lineTo(x, top + th);
      c.lineTo(x, top + th + lift);
      c.lineTo(x - tw / 2, top + th / 2 + lift);
      c.closePath();
      c.fill();

      c.fillStyle = style.right;
      c.beginPath();
      c.moveTo(x + tw / 2, top + th / 2);
      c.lineTo(x, top + th);
      c.lineTo(x, top + th + lift);
      c.lineTo(x + tw / 2, top + th / 2 + lift);
      c.closePath();
      c.fill();
    }

    // Subtle per-tile value variation stops large sand fields looking like plastic.
    const jitter = ((wx * 73856093) ^ (wy * 19349663)) & 15;
    c.fillStyle = jitter > 12 ? mix(style.top, style.right, 0.35) : style.top;
    c.beginPath();
    c.moveTo(x, top);
    c.lineTo(x + tw / 2, top + th / 2);
    c.lineTo(x, top + th);
    c.lineTo(x - tw / 2, top + th / 2);
    c.closePath();
    c.fill();

    if (isRail(map, wx, wy)) {
      this.drawRail(c, x, top, tw, th);
    }
  }

  /** Rail sleepers and two steel rails, drawn along the tile's long axis. */
  private drawRail(c: CanvasRenderingContext2D, x: number, y: number, tw: number, th: number): void {
    if (tw < 12) {
      c.fillStyle = '#5b5348';
      c.fillRect(x - tw / 4, y + th / 2 - 1, tw / 2, 2);
      return;
    }
    c.strokeStyle = '#5b5348';
    c.lineWidth = Math.max(1, tw * 0.035);
    for (let s = -2; s <= 2; s++) {
      const t = s / 5;
      const sx = x + t * (tw / 2);
      const sy = y + th / 2 + t * (th / 2);
      c.beginPath();
      c.moveTo(sx - tw * 0.09, sy + th * 0.09);
      c.lineTo(sx + tw * 0.09, sy - th * 0.09);
      c.stroke();
    }
    c.strokeStyle = '#8f9296';
    c.lineWidth = Math.max(1, tw * 0.022);
    for (const off of [-0.07, 0.07]) {
      c.beginPath();
      c.moveTo(x - tw / 2 + off * tw, y + off * th);
      c.lineTo(x + tw / 2 + off * tw, y + th + off * th - th);
      c.stroke();
    }
  }

  /**
   * Animated specular bands over water. Drawn live rather than baked into chunks
   * so the sea moves; it is cheap because it only covers visible water tiles.
   */
  private drawWaterShimmer(ctx: CanvasRenderingContext2D, camera: Camera, time: number): void {
    if (camera.zoom < 0.35) return;
    const b = camera.visibleBounds(2);
    ctx.fillStyle = WATER_HIGHLIGHT;
    const tw = TILE_W * camera.zoom;
    const th = TILE_H * camera.zoom;
    const step = camera.zoom < 0.7 ? 2 : 1;

    for (let wy = b.y0; wy <= b.y1; wy += step) {
      for (let wx = b.x0; wx <= b.x1; wx += step) {
        const t = this.map.terrain[idx(wx, wy)];
        if (t !== Terrain.Water && t !== Terrain.DeepWater) continue;
        const phase = Math.sin(time * 1.4 + wx * 0.35 + wy * 0.21);
        if (phase < 0.55) continue;
        const p = camera.worldToScreen(wx, wy);
        ctx.beginPath();
        ctx.ellipse(p.x, p.y + th / 2, tw * 0.22, th * 0.2, 0, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  }

  invalidate(): void {
    this.cache.clear();
  }
}
