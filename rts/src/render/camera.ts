import { TILE_W, TILE_H, MAP_WIDTH, MAP_HEIGHT } from '../data/config';

/**
 * Isometric camera and coordinate transforms.
 *
 * World space is measured in tiles. Screen space is pixels. The projection is a
 * standard 2:1 dimetric ("isometric") one, which is what gives Desert Order its
 * three-quarter-view look while keeping the maths cheap enough for a 512x512 map.
 */

export interface Point {
  x: number;
  y: number;
}

export class Camera {
  /** Centre of the view, in world tile coordinates. */
  x = MAP_WIDTH / 2;
  y = MAP_HEIGHT / 2;
  zoom = 1;

  viewWidth = 0;
  viewHeight = 0;

  readonly minZoom = 0.18;
  readonly maxZoom = 2.6;

  resize(width: number, height: number): void {
    this.viewWidth = width;
    this.viewHeight = height;
  }

  /** World tile -> screen pixel. */
  worldToScreen(wx: number, wy: number): Point {
    const isoX = (wx - wy) * (TILE_W / 2);
    const isoY = (wx + wy) * (TILE_H / 2);
    const originX = (this.x - this.y) * (TILE_W / 2);
    const originY = (this.x + this.y) * (TILE_H / 2);
    return {
      x: (isoX - originX) * this.zoom + this.viewWidth / 2,
      y: (isoY - originY) * this.zoom + this.viewHeight / 2,
    };
  }

  /** Screen pixel -> world tile. Inverse of the projection above. */
  screenToWorld(sx: number, sy: number): Point {
    const originX = (this.x - this.y) * (TILE_W / 2);
    const originY = (this.x + this.y) * (TILE_H / 2);
    const isoX = (sx - this.viewWidth / 2) / this.zoom + originX;
    const isoY = (sy - this.viewHeight / 2) / this.zoom + originY;
    const a = isoX / (TILE_W / 2);
    const b = isoY / (TILE_H / 2);
    return { x: (a + b) / 2, y: (b - a) / 2 };
  }

  panBy(dxPixels: number, dyPixels: number): void {
    // Convert a screen-space drag into world-space movement.
    const a = dxPixels / this.zoom / (TILE_W / 2);
    const b = dyPixels / this.zoom / (TILE_H / 2);
    this.x -= (a + b) / 2;
    this.y -= (b - a) / 2;
    this.clamp();
  }

  /** Zooms while keeping the world point under the cursor fixed. */
  zoomAt(sx: number, sy: number, factor: number): void {
    const before = this.screenToWorld(sx, sy);
    this.zoom = Math.max(this.minZoom, Math.min(this.maxZoom, this.zoom * factor));
    const after = this.screenToWorld(sx, sy);
    this.x += before.x - after.x;
    this.y += before.y - after.y;
    this.clamp();
  }

  centreOn(wx: number, wy: number): void {
    this.x = wx;
    this.y = wy;
    this.clamp();
  }

  private clamp(): void {
    this.x = Math.max(0, Math.min(MAP_WIDTH, this.x));
    this.y = Math.max(0, Math.min(MAP_HEIGHT, this.y));
  }

  /**
   * Returns the inclusive tile bounds currently visible, with a margin.
   * Culling to this range is what makes a 262144-tile map render at 60fps.
   */
  visibleBounds(margin = 3): { x0: number; y0: number; x1: number; y1: number } {
    const corners = [
      this.screenToWorld(0, 0),
      this.screenToWorld(this.viewWidth, 0),
      this.screenToWorld(0, this.viewHeight),
      this.screenToWorld(this.viewWidth, this.viewHeight),
    ];
    const xs = corners.map((c) => c.x);
    const ys = corners.map((c) => c.y);
    return {
      x0: Math.max(0, Math.floor(Math.min(...xs)) - margin),
      y0: Math.max(0, Math.floor(Math.min(...ys)) - margin),
      x1: Math.min(MAP_WIDTH - 1, Math.ceil(Math.max(...xs)) + margin),
      y1: Math.min(MAP_HEIGHT - 1, Math.ceil(Math.max(...ys)) + margin),
    };
  }

  isVisible(wx: number, wy: number, pad = 120): boolean {
    const p = this.worldToScreen(wx, wy);
    return p.x >= -pad && p.y >= -pad && p.x <= this.viewWidth + pad && p.y <= this.viewHeight + pad;
  }
}
