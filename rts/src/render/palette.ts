import { Terrain } from '../core/worldmap';

/**
 * Art direction.
 *
 * All artwork in this game is drawn procedurally from these palettes rather than
 * imported from anywhere -- the visual language is a modernised desert-warfare look
 * (warm sand, cool shadow, desaturated military hardware) that is entirely original.
 */

export interface TerrainStyle {
  /** Top face of the tile. */
  top: string;
  /** Left-facing side, in shadow. */
  left: string;
  /** Right-facing side, catching light. */
  right: string;
}

export const TERRAIN_STYLES: Record<number, TerrainStyle> = {
  [Terrain.DeepWater]: { top: '#12384f', left: '#0c2739', right: '#164764' },
  [Terrain.Water]: { top: '#1d5b78', left: '#144257', right: '#26708f' },
  [Terrain.Beach]: { top: '#e8d09a', left: '#c2a973', right: '#f2dcaa' },
  [Terrain.Sand]: { top: '#d9b878', left: '#b2925a', right: '#e5c98d' },
  [Terrain.Dunes]: { top: '#e3c489', right: '#efd6a1', left: '#bb9c62' },
  [Terrain.Rock]: { top: '#9a8367', left: '#75624b', right: '#ab9576' },
  [Terrain.Mountain]: { top: '#7d6b57', left: '#5a4c3d', right: '#8f7c65' },
  [Terrain.Scrub]: { top: '#a8a566', left: '#83814d', right: '#b9b678' },
};

/** Colours for the world outside any terrain, i.e. the void beyond the map edge. */
export const VOID_COLOUR = '#0a1219';

/** Water animates; these are the highlight bands. */
export const WATER_HIGHLIGHT = 'rgba(150, 215, 240, 0.16)';

export const UI = {
  panel: 'rgba(18, 24, 30, 0.92)',
  panelBorder: 'rgba(216, 178, 112, 0.35)',
  text: '#e9e2d4',
  textDim: '#9aa2a8',
  accent: '#d9a441',
  accentSoft: 'rgba(217, 164, 65, 0.18)',
  good: '#7fd98d',
  bad: '#e8695f',
  selection: '#8fe3a4',
  steel: '#b8c4cc',
  aluminum: '#9fd0e0',
  fuel: '#e8a95f',
};

/** Faction hull tints, derived from the player colour so units read at a glance. */
export function hullColours(playerColour: string): { hull: string; dark: string; light: string } {
  return {
    hull: mix(playerColour, '#6b6f5e', 0.55),
    dark: mix(playerColour, '#22261f', 0.72),
    light: mix(playerColour, '#cfd3bd', 0.45),
  };
}

/** Linear blend between two hex colours. t=0 returns a, t=1 returns b. */
export function mix(a: string, b: string, t: number): string {
  const pa = parseHex(a);
  const pb = parseHex(b);
  const r = Math.round(pa[0] + (pb[0] - pa[0]) * t);
  const g = Math.round(pa[1] + (pb[1] - pa[1]) * t);
  const bl = Math.round(pa[2] + (pb[2] - pa[2]) * t);
  return `rgb(${r}, ${g}, ${bl})`;
}

function parseHex(hex: string): [number, number, number] {
  const clean = hex.replace('#', '');
  const full = clean.length === 3 ? clean.split('').map((c) => c + c).join('') : clean;
  return [
    parseInt(full.slice(0, 2), 16),
    parseInt(full.slice(2, 4), 16),
    parseInt(full.slice(4, 6), 16),
  ];
}

export function withAlpha(colour: string, alpha: number): string {
  const [r, g, b] = parseHex(colour);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** Structure palettes by building role, so a base reads as a real installation. */
export const STRUCTURE_STYLES: Record<string, { wall: string; roof: string; trim: string }> = {
  command: { wall: '#a8a08c', roof: '#5f6b58', trim: '#d9a441' },
  power: { wall: '#9aa0a6', roof: '#4d5a63', trim: '#f0c04a' },
  military: { wall: '#8f8b76', roof: '#57604a', trim: '#c0523f' },
  steel_mine: { wall: '#8a8377', roof: '#6b6257', trim: '#b8c4cc' },
  aluminum_mine: { wall: '#8e9096', roof: '#646a70', trim: '#9fd0e0' },
  fuel_pump: { wall: '#8d8375', roof: '#5c5348', trim: '#e8a95f' },
  steel_depot: { wall: '#96907f', roof: '#6e6a5c', trim: '#b8c4cc' },
  aluminum_depot: { wall: '#949aa0', roof: '#686e74', trim: '#9fd0e0' },
  fuel_depot: { wall: '#8f857a', roof: '#5f564c', trim: '#e8a95f' },
  factory: { wall: '#87826f', roof: '#535c46', trim: '#d9a441' },
  research: { wall: '#a0a2ad', roof: '#5a5d72', trim: '#8fb8ff' },
  radar: { wall: '#9aa0a6', roof: '#525a61', trim: '#7fd98d' },
  defense_tower: { wall: '#7f7a6a', roof: '#4a4d3f', trim: '#e8695f' },
  wall: { wall: '#9c9583', roof: '#6a6656', trim: '#8d8574' },
  repair_bay: { wall: '#8b8f84', roof: '#525a4f', trim: '#7fd98d' },
};
