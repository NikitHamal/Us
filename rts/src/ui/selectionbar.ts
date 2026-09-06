import type { GameState } from '../core/state';
import { UNIT_BY_ID } from '../data/units';
import { formatNumber, formatPercent, escapeHtml } from './format';
import { offensiveSpecs } from '../data/combat';

/**
 * The bottom selection bar.
 *
 * Groups the current selection by unit type so a fifty-tank army reads as a handful of
 * chips rather than fifty icons, and summarises the combined statistics of the force.
 */

export interface SelectionBarCallbacks {
  onSelectSubset: (unitIds: number[]) => void;
  onStop: () => void;
  onRecall: () => void;
}

export class SelectionBar {
  private readonly root: HTMLElement;
  private readonly head: HTMLElement;
  private readonly chips: HTMLElement;
  private lastSignature = '';

  private readonly cb: SelectionBarCallbacks;

  constructor(parent: HTMLElement, cb: SelectionBarCallbacks) {
    this.cb = cb;
    this.root = document.createElement('div');
    this.root.className = 'selection panel';
    this.root.style.display = 'none';

    this.head = document.createElement('div');
    this.head.className = 'head';
    this.root.appendChild(this.head);

    this.chips = document.createElement('div');
    this.chips.className = 'chips';
    this.root.appendChild(this.chips);

    parent.appendChild(this.root);
  }

  update(state: GameState, selected: Set<number>): void {
    if (selected.size === 0) {
      this.root.style.display = 'none';
      this.lastSignature = '';
      return;
    }
    this.root.style.display = 'flex';

    const units = state.units.filter((u) => selected.has(u.id));
    if (units.length === 0) {
      this.root.style.display = 'none';
      return;
    }

    // Only rebuild when the composition actually changes.
    const signature = units.map((u) => u.defId).sort().join('|') + `:${units.length}`;
    const grouped = new Map<string, number[]>();
    for (const u of units) {
      const list = grouped.get(u.defId) ?? [];
      list.push(u.id);
      grouped.set(u.defId, list);
    }

    // Aggregate combat statistics for the whole selection.
    let damage = 0;
    let hp = 0;
    let maxHp = 0;
    let fuelFraction = 0;
    for (const u of units) {
      const def = UNIT_BY_ID[u.defId];
      if (!def) continue;
      damage += def.damage;
      hp += u.hp;
      maxHp += u.maxHp;
      fuelFraction += u.fuel / Math.max(1, u.maxFuel);
    }
    fuelFraction /= units.length;

    this.head.innerHTML = `
      <div class="label">Selection</div>
      <div style="font-weight:700">${units.length} unit${units.length > 1 ? 's' : ''}</div>
      <div class="meta" style="display:flex;gap:14px;color:var(--dim);font-size:12px">
        <span>Firepower <b style="color:var(--text)">${formatNumber(damage)}</b></span>
        <span>Integrity <b style="color:var(--text)">${formatPercent(hp / Math.max(1, maxHp))}</b></span>
        <span>Fuel <b style="color:${fuelFraction < 0.2 ? 'var(--bad)' : 'var(--text)'}">${formatPercent(fuelFraction)}</b></span>
      </div>
      <div style="flex:1"></div>
      <button class="action ghost" data-act="stop">Stop</button>
      <button class="action ghost" data-act="recall">Recall</button>`;

    this.head.querySelector('[data-act="stop"]')?.addEventListener('click', () => this.cb.onStop());
    this.head.querySelector('[data-act="recall"]')?.addEventListener('click', () => this.cb.onRecall());

    if (signature === this.lastSignature) return;
    this.lastSignature = signature;

    this.chips.innerHTML = '';
    for (const [defId, ids] of grouped) {
      const def = UNIT_BY_ID[defId];
      if (!def) continue;
      const chip = document.createElement('div');
      chip.className = 'chip';
      const specs = offensiveSpecs(def).map((s) => s.replace('vs_', 'vs ')).join(', ');
      chip.title = `${def.name}\n${def.description}${specs ? `\nStrong against: ${specs}` : ''}`;
      chip.innerHTML = `<span class="n">${ids.length}</span> ${escapeHtml(shortName(def.name))}`;
      chip.addEventListener('click', () => this.cb.onSelectSubset(ids));
      this.chips.appendChild(chip);
    }
  }
}

/** Long unit names are trimmed to their distinctive part for the chips. */
function shortName(name: string): string {
  return name.length <= 18 ? name : `${name.slice(0, 17)}\u2026`;
}
