import type { GameState } from '../core/state';
import type { WorldMap } from '../core/worldmap';
import { Terrain, idx } from '../core/worldmap';
import type { Camera } from '../render/camera';
import { MAP_WIDTH, MAP_HEIGHT } from '../data/config';
import { TERRAIN_STYLES } from '../render/palette';

/**
 * Strategic minimap.
 *
 * The terrain layer is rasterised once into an offscreen bitmap at generation time --
 * it never changes -- and only the dynamic overlay (units, bases, viewport) is
 * redrawn each frame.
 */

const SIZE = 220;

export class Minimap {
  readonly canvas: HTMLCanvasElement;
  private readonly ctx: CanvasRenderingContext2D;
  private readonly terrainLayer: HTMLCanvasElement;

  private readonly onJump: (x: number, y: number) => void;

  constructor(parent: HTMLElement, map: WorldMap, onJump: (x: number, y: number) => void) {
    this.onJump = onJump;
    const wrap = document.createElement('div');
    wrap.className = 'minimap-wrap panel';

    this.canvas = document.createElement('canvas');
    this.canvas.id = 'minimap';
    this.canvas.width = SIZE;
    this.canvas.height = SIZE;
    wrap.appendChild(this.canvas);
    parent.appendChild(wrap);

    const ctx = this.canvas.getContext('2d');
    if (!ctx) throw new Error('Minimap canvas unavailable.');
    this.ctx = ctx;

    this.terrainLayer = this.bakeTerrain(map);

    const jump = (e: MouseEvent) => {
      const r = this.canvas.getBoundingClientRect();
      const x = ((e.clientX - r.left) / r.width) * MAP_WIDTH;
      const y = ((e.clientY - r.top) / r.height) * MAP_HEIGHT;
      this.onJump(x, y);
    };
    this.canvas.addEventListener('mousedown', jump);
    this.canvas.addEventListener('mousemove', (e) => {
      if (e.buttons & 1) jump(e);
    });
  }

  /** Renders the static terrain once using direct pixel writes for speed. */
  private bakeTerrain(map: WorldMap): HTMLCanvasElement {
    const layer = document.createElement('canvas');
    layer.width = SIZE;
    layer.height = SIZE;
    const c = layer.getContext('2d');
    if (!c) return layer;

    const image = c.createImageData(SIZE, SIZE);
    const data = image.data;
    const sx = MAP_WIDTH / SIZE;
    const sy = MAP_HEIGHT / SIZE;

    for (let py = 0; py < SIZE; py++) {
      for (let px = 0; px < SIZE; px++) {
        const wx = Math.min(MAP_WIDTH - 1, Math.floor(px * sx));
        const wy = Math.min(MAP_HEIGHT - 1, Math.floor(py * sy));
        const i = idx(wx, wy);
        const terrain = map.terrain[i];
        const style = TERRAIN_STYLES[terrain] ?? TERRAIN_STYLES[Terrain.Sand];
        const [r, g, b] = parseRgb(style.top);
        // Shade by elevation so the relief is readable at a glance.
        const shade = 0.72 + map.height01[i] * 0.5;
        const o = (py * SIZE + px) * 4;
        data[o] = Math.min(255, r * shade);
        data[o + 1] = Math.min(255, g * shade);
        data[o + 2] = Math.min(255, b * shade);
        data[o + 3] = 255;
      }
    }
    c.putImageData(image, 0, 0);

    // Rail overlay
    c.fillStyle = 'rgba(60, 55, 48, 0.85)';
    for (let wy = 0; wy < MAP_HEIGHT; wy += 1) {
      for (let wx = 0; wx < MAP_WIDTH; wx += 1) {
        if (map.rail[idx(wx, wy)] === 1) {
          c.fillRect((wx / MAP_WIDTH) * SIZE, (wy / MAP_HEIGHT) * SIZE, 1, 1);
        }
      }
    }
    return layer;
  }

  update(state: GameState, camera: Camera, viewerId: number): void {
    const ctx = this.ctx;
    ctx.clearRect(0, 0, SIZE, SIZE);
    ctx.drawImage(this.terrainLayer, 0, 0);

    const toMap = (wx: number, wy: number) => ({
      x: (wx / MAP_WIDTH) * SIZE,
      y: (wy / MAP_HEIGHT) * SIZE,
    });

    // Territories
    for (const t of state.territories) {
      const p = toMap(t.wx, t.wy);
      const owner = t.owner === null ? null : state.players.find((x) => x.id === t.owner);
      ctx.fillStyle = owner ? owner.colour : 'rgba(200,200,200,0.45)';
      ctx.fillRect(p.x - 1, p.y - 1, 2, 2);
    }

    // Bases
    for (const b of state.bases) {
      const p = toMap(b.wx, b.wy);
      const owner = state.players.find((x) => x.id === b.owner);
      ctx.fillStyle = owner?.colour ?? '#fff';
      ctx.fillRect(p.x - 3, p.y - 3, 6, 6);
      ctx.strokeStyle = 'rgba(0,0,0,0.6)';
      ctx.lineWidth = 1;
      ctx.strokeRect(p.x - 3, p.y - 3, 6, 6);
    }

    // Units
    for (const u of state.units) {
      const owner = state.players.find((x) => x.id === u.owner);
      if (!owner) continue;
      const p = toMap(u.x, u.y);
      ctx.fillStyle = owner.colour;
      ctx.fillRect(p.x - 1, p.y - 1, 2.5, 2.5);
    }

    // Viewport rectangle
    const b = camera.visibleBounds(0);
    const tl = toMap(b.x0, b.y0);
    const br = toMap(b.x1, b.y1);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.75)';
    ctx.lineWidth = 1.5;
    ctx.strokeRect(tl.x, tl.y, br.x - tl.x, br.y - tl.y);
    void viewerId;
  }
}

function parseRgb(hex: string): [number, number, number] {
  const c = hex.replace('#', '');
  return [
    parseInt(c.slice(0, 2), 16),
    parseInt(c.slice(2, 4), 16),
    parseInt(c.slice(4, 6), 16),
  ];
}
