import type { GameState } from '../core/state';
import { formatNumber, formatRate, formatClock, escapeHtml } from './format';
import { playerIncome } from '../sim/economy';
import { SPEED_OPTIONS } from '../data/config';
import type { SpeedPreset } from '../data/config';

/**
 * Top bar and event log.
 *
 * The HUD writes into pre-built DOM rather than re-creating elements each frame,
 * which keeps a 60fps update loop from generating garbage.
 */

export interface HudCallbacks {
  onSpeedChange: (speed: SpeedPreset) => void;
  onTogglePause: () => void;
}

export class Hud {
  private readonly root: HTMLElement;
  private readonly amounts: Record<string, HTMLElement> = {};
  private readonly rates: Record<string, HTMLElement> = {};
  private readonly clock: HTMLElement;
  private readonly pauseButton: HTMLButtonElement;
  private readonly speedButtons = new Map<SpeedPreset, HTMLButtonElement>();
  private readonly logEl: HTMLElement;
  private lastLogLength = -1;

  constructor(parent: HTMLElement, callbacks: HudCallbacks) {
    this.root = document.createElement('div');
    this.root.className = 'topbar panel';

    const resources = document.createElement('div');
    resources.className = 'resources';
    for (const [key, cls, label] of [
      ['steel', 'steel', 'Steel'],
      ['aluminum', 'alu', 'Aluminium'],
      ['fuel', 'fuel', 'Fuel'],
    ] as const) {
      const el = document.createElement('div');
      el.className = `res ${cls}`;
      el.innerHTML = `
        <span class="dot"></span>
        <div>
          <div class="amount">0</div>
          <div class="label">${label}</div>
        </div>
        <div class="rate">+0/s</div>`;
      this.amounts[key] = el.querySelector('.amount') as HTMLElement;
      this.rates[key] = el.querySelector('.rate') as HTMLElement;
      resources.appendChild(el);
    }
    this.root.appendChild(resources);

    const spacer = document.createElement('div');
    spacer.className = 'spacer';
    this.root.appendChild(spacer);

    this.clock = document.createElement('div');
    this.clock.className = 'clock';
    this.clock.textContent = '00:00';
    this.root.appendChild(this.clock);

    this.pauseButton = document.createElement('button');
    this.pauseButton.className = 'action ghost';
    this.pauseButton.textContent = 'Pause';
    this.pauseButton.addEventListener('click', () => callbacks.onTogglePause());
    this.root.appendChild(this.pauseButton);

    const speedGroup = document.createElement('div');
    speedGroup.className = 'speed-group';
    for (const opt of SPEED_OPTIONS) {
      const b = document.createElement('button');
      b.textContent = opt.label;
      b.title = opt.description;
      b.addEventListener('click', () => callbacks.onSpeedChange(opt.id));
      speedGroup.appendChild(b);
      this.speedButtons.set(opt.id, b);
    }
    this.root.appendChild(speedGroup);

    parent.appendChild(this.root);

    this.logEl = document.createElement('div');
    this.logEl.className = 'log panel';
    parent.appendChild(this.logEl);
  }

  update(state: GameState, viewerId: number, paused: boolean): void {
    const player = state.players.find((p) => p.id === viewerId);
    if (!player) return;

    const income = playerIncome(state, viewerId);
    this.amounts.steel.textContent = formatNumber(player.resources.steel);
    this.amounts.aluminum.textContent = formatNumber(player.resources.aluminum);
    this.amounts.fuel.textContent = formatNumber(player.resources.fuel);
    this.rates.steel.textContent = formatRate(income.steel);
    this.rates.aluminum.textContent = formatRate(income.aluminum);
    this.rates.fuel.textContent = formatRate(income.fuel);

    this.clock.textContent = formatClock(state.time);
    this.pauseButton.textContent = paused ? 'Resume' : 'Pause';

    for (const [id, button] of this.speedButtons) {
      button.classList.toggle('active', state.speed === id);
    }

    if (state.log.length !== this.lastLogLength) {
      this.lastLogLength = state.log.length;
      const recent = state.log.slice(-8).reverse();
      this.logEl.innerHTML = recent
        .map((e) => `<div class="entry ${e.severity}">${escapeHtml(e.text)}</div>`)
        .join('');
    }
  }
}
