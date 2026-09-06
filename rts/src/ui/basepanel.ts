import type { GameState, Base } from '../core/state';
import { canAfford } from '../core/state';
import { BUILDINGS, BUILDING_BY_ID, upgradeCost, upgradeTime, output, powerOutput } from '../data/buildings';
import { unitsForBase } from '../data/units';
import { RESEARCH, researchCost, researchTime } from '../data/research';
import { BASE_TYPE_BY_KIND } from '../data/bases';
import { requiredMilitaryLevel } from '../sim/production';
import { playerSlots } from '../sim/economy';
import { offensiveSpecs } from '../data/combat';
import { formatNumber, formatDuration, escapeHtml } from './format';
import type { Resources } from '../data/types';

/**
 * The base management panel: the screen where most of the game is actually played.
 *
 * Renders three tabs -- Structures, Production, Research -- for the selected base,
 * and emits intents through callbacks rather than touching the simulation directly.
 */

export type PanelTab = 'structures' | 'production' | 'research' | 'intel';

export interface BasePanelCallbacks {
  onBuild: (baseId: number, buildingId: string) => void;
  onQueueUnit: (baseId: number, unitDefId: string, count: number) => void;
  onCancelQueue: (baseId: number, index: number) => void;
  onResearch: (researchId: string) => void;
  onDeploy: (baseId: number, unitDefId: string, count: number) => void;
  onSelectBase: (baseId: number) => void;
}

export class BasePanel {
  private readonly root: HTMLElement;
  private readonly body: HTMLElement;
  private readonly tabButtons = new Map<PanelTab, HTMLButtonElement>();
  private tab: PanelTab = 'structures';
  /** Rebuilding the DOM every frame is wasteful; refresh on a timer instead. */
  private lastRender = 0;

  private readonly cb: BasePanelCallbacks;

  constructor(parent: HTMLElement, cb: BasePanelCallbacks) {
    this.cb = cb;
    this.root = document.createElement('div');
    this.root.className = 'sidepanel panel';

    const tabs = document.createElement('div');
    tabs.className = 'tabs';
    const labels: Array<[PanelTab, string]> = [
      ['structures', 'Base'],
      ['production', 'Units'],
      ['research', 'Research'],
      ['intel', 'Intel'],
    ];
    for (const [id, label] of labels) {
      const b = document.createElement('button');
      b.textContent = label;
      b.addEventListener('click', () => {
        this.tab = id;
        this.lastRender = 0;
      });
      tabs.appendChild(b);
      this.tabButtons.set(id, b);
    }
    this.root.appendChild(tabs);

    this.body = document.createElement('div');
    this.body.className = 'panel-body';
    this.root.appendChild(this.body);
    parent.appendChild(this.root);
  }

  update(state: GameState, viewerId: number, base: Base | null, now: number): void {
    for (const [id, b] of this.tabButtons) b.classList.toggle('active', id === this.tab);
    // Four refreshes a second is smooth to the eye and cheap.
    if (now - this.lastRender < 0.25) return;
    this.lastRender = now;

    if (this.tab === 'research') {
      this.body.innerHTML = this.renderResearch(state, viewerId);
    } else if (this.tab === 'intel') {
      this.body.innerHTML = this.renderIntel(state, viewerId);
    } else if (!base) {
      this.body.innerHTML =
        '<div class="empty">No base selected.<br>Click one of your bases on the map to manage it.</div>';
    } else if (this.tab === 'structures') {
      this.body.innerHTML = this.renderStructures(state, viewerId, base);
    } else {
      this.body.innerHTML = this.renderProduction(state, viewerId, base);
    }

    this.bindActions(state, base);
  }

  /** Wires up the buttons produced by the innerHTML render pass. */
  private bindActions(state: GameState, base: Base | null): void {
    for (const el of this.body.querySelectorAll<HTMLButtonElement>('[data-build]')) {
      el.addEventListener('click', () => base && this.cb.onBuild(base.id, el.dataset.build!));
    }
    for (const el of this.body.querySelectorAll<HTMLButtonElement>('[data-queue]')) {
      const count = Number(el.dataset.count ?? '1');
      el.addEventListener('click', () => base && this.cb.onQueueUnit(base.id, el.dataset.queue!, count));
    }
    for (const el of this.body.querySelectorAll<HTMLButtonElement>('[data-cancel]')) {
      el.addEventListener('click', () => base && this.cb.onCancelQueue(base.id, Number(el.dataset.cancel)));
    }
    for (const el of this.body.querySelectorAll<HTMLButtonElement>('[data-research]')) {
      el.addEventListener('click', () => this.cb.onResearch(el.dataset.research!));
    }
    for (const el of this.body.querySelectorAll<HTMLButtonElement>('[data-deploy]')) {
      const count = Number(el.dataset.count ?? '1');
      el.addEventListener('click', () => base && this.cb.onDeploy(base.id, el.dataset.deploy!, count));
    }
    for (const el of this.body.querySelectorAll<HTMLElement>('[data-base]')) {
      el.addEventListener('click', () => this.cb.onSelectBase(Number(el.dataset.base)));
    }
    void state;
  }

  // -------------------------------------------------------------- structures

  private renderStructures(state: GameState, viewerId: number, base: Base): string {
    const player = state.players.find((p) => p.id === viewerId);
    if (!player) return '';
    const typeDef = BASE_TYPE_BY_KIND[base.kind];
    const slots = playerSlots(state, viewerId);
    const power = base.cache.powerSupply - base.cache.powerDraw;

    const header = `
      <div class="section-title">${escapeHtml(base.name)}</div>
      <div class="row" style="border-color: var(--border-soft)">
        <div class="grow">
          <div class="name">${escapeHtml(typeDef.name)}</div>
          <div class="meta">
            <span>Command lv ${base.cache.commandLevel}</span>
            <span>Slots ${slots.used}/${slots.cap}</span>
            <span style="color:${power < 0 ? 'var(--bad)' : 'var(--good)'}">
              Power ${power >= 0 ? '+' : ''}${Math.round(power)}
            </span>
          </div>
        </div>
      </div>
      <div class="section-title" style="margin-top:16px">Structures</div>`;

    const rows = BUILDINGS.filter((def) => def.allowedBases.includes(base.kind))
      .map((def) => {
        const instance = base.buildings.find((b) => b.defId === def.id);
        const level = instance?.level ?? 0;
        const target = level + 1;
        const capped =
          def.kind !== 'command' && target > base.cache.commandLevel
            ? 'Requires Command Base level ' + target
            : target > def.maxLevel
              ? 'Maximum level reached'
              : null;
        const cost = upgradeCost(def, Math.min(target, def.maxLevel));
        const affordable = canAfford(player.resources, cost);
        const time = upgradeTime(def, Math.min(target, def.maxLevel));

        let progress = '';
        if (instance?.upgrading) {
          const total = upgradeTime(def, instance.upgrading.targetLevel);
          const pct = Math.max(0, Math.min(1, 1 - instance.upgrading.remaining / total));
          progress = `<div class="progress"><div style="width:${(pct * 100).toFixed(1)}%"></div></div>
            <div class="meta"><span>Upgrading to ${instance.upgrading.targetLevel} &middot;
            ${formatDuration(instance.upgrading.remaining)}</span></div>`;
        }

        const effect = this.effectSummary(def.id, level);
        const button = instance?.upgrading
          ? '<button class="action" disabled>Building</button>'
          : capped
            ? `<button class="action" disabled title="${escapeHtml(capped)}">Locked</button>`
            : `<button class="action" data-build="${def.id}" ${affordable ? '' : 'disabled'}>
                 ${level === 0 ? 'Build' : `Lv ${target}`}
               </button>`;

        return `
          <div class="row">
            <span class="lvl">${level}</span>
            <div class="grow">
              <div class="name">${escapeHtml(def.name)}${instance?.disabled ? ' <span class="tag">destroyed</span>' : ''}</div>
              <div class="meta">${effect ? `<span>${escapeHtml(effect)}</span>` : ''}</div>
              <div class="cost">${this.costHtml(cost, player.resources)}
                <span style="color:var(--dim)">${formatDuration(time)}</span></div>
              ${progress}
            </div>
            ${button}
          </div>`;
      })
      .join('');

    return header + rows;
  }

  /** One-line description of what the next level actually gives you. */
  private effectSummary(defId: string, level: number): string {
    const next = level + 1;
    switch (defId) {
      case 'steel_mine':
        return `${formatNumber(output('steel_mine', level))}/s -> ${formatNumber(output('steel_mine', next))}/s`;
      case 'aluminum_mine':
        return `${formatNumber(output('aluminum_mine', level))}/s -> ${formatNumber(output('aluminum_mine', next))}/s`;
      case 'fuel_pump':
        return `${formatNumber(output('fuel_pump', level))}/s -> ${formatNumber(output('fuel_pump', next))}/s`;
      case 'power_plant':
        return `${Math.round(powerOutput(level))} -> ${Math.round(powerOutput(next))} power`;
      default:
        return BUILDING_BY_ID[defId]?.effect.split('.')[0] ?? '';
    }
  }

  // -------------------------------------------------------------- production

  private renderProduction(state: GameState, viewerId: number, base: Base): string {
    const player = state.players.find((p) => p.id === viewerId);
    if (!player) return '';
    const military = base.buildings.find((b) => b.defId === 'military_central');
    const militaryLevel = military?.level ?? 0;
    const slots = playerSlots(state, viewerId);

    let html = `<div class="section-title">Production queue
      <span style="float:right;color:var(--dim);font-weight:600">
        ${base.queue.length} queued &middot; ${base.cache.queueSlots} lines</span></div>`;

    if (base.queue.length === 0) {
      html += '<div class="empty" style="padding:14px">Nothing in production.</div>';
    } else {
      html += base.queue
        .slice(0, 10)
        .map((q, i) => {
          const def = unitsForBase(base.kind).find((u) => u.id === q.unitDefId);
          const pct = Math.max(0, Math.min(1, 1 - q.remaining / q.total));
          const active = i < base.cache.queueSlots;
          return `
            <div class="row">
              <div class="grow">
                <div class="name">${escapeHtml(def?.name ?? q.unitDefId)}</div>
                <div class="meta"><span>${active ? formatDuration(q.remaining) : 'Waiting'}</span></div>
                ${active ? `<div class="progress"><div style="width:${(pct * 100).toFixed(1)}%"></div></div>` : ''}
              </div>
              <button class="action ghost" data-cancel="${i}">Cancel</button>
            </div>`;
        })
        .join('');
    }

    // Garrison
    const garrison = Object.entries(base.garrison).filter(([, n]) => n > 0);
    if (garrison.length > 0) {
      html += '<div class="section-title" style="margin-top:16px">Garrison</div>';
      html += garrison
        .map(([defId, n]) => {
          const def = unitsForBase(base.kind).find((u) => u.id === defId);
          return `
            <div class="row">
              <span class="lvl">${n}</span>
              <div class="grow"><div class="name">${escapeHtml(def?.name ?? defId)}</div></div>
              <button class="action" data-deploy="${defId}" data-count="${n}">Deploy all</button>
            </div>`;
        })
        .join('');
    }

    html += `<div class="section-title" style="margin-top:16px">Available units
      <span style="float:right;color:var(--dim);font-weight:600">Slots ${slots.used}/${slots.cap}</span></div>`;

    const units = unitsForBase(base.kind).sort(
      (a, b) => a.cost.steel + a.cost.aluminum - (b.cost.steel + b.cost.aluminum),
    );

    html += units
      .map((u) => {
        const required = requiredMilitaryLevel(u.id);
        const locked = militaryLevel < required;
        const cost: Resources = { steel: u.cost.steel, aluminum: u.cost.aluminum, fuel: 0 };
        const affordable = canAfford(player.resources, cost);
        const specs = offensiveSpecs(u)
          .map((s) => `<span class="tag spec">${s.replace('vs_', 'vs ')}</span>`)
          .join('');
        const stealth = u.specialisations.includes('stealth')
          ? '<span class="tag stealth">stealth</span>'
          : '';
        const detector = u.specialisations.includes('detector')
          ? '<span class="tag">detector</span>'
          : '';
        const rangeNote = u.rangeEngageMultiplier ? ` (x${u.rangeEngageMultiplier})` : '';

        return `
          <div class="row" title="${escapeHtml(u.description)}">
            <div class="grow">
              <div class="name">${escapeHtml(u.name)} ${specs}${stealth}${detector}
                ${locked ? `<span class="tag locked">MC ${required}</span>` : ''}</div>
              <div class="meta">
                <span>DMG ${formatNumber(u.damage)}</span>
                <span>ARM ${formatNumber(u.armor)}</span>
                <span>RNG ${u.range}${rangeNote}</span>
                <span>FUEL ${u.fuel}/s</span>
              </div>
              <div class="cost">${this.costHtml(cost, player.resources)}
                <span style="color:var(--dim)">${formatDuration(u.buildTime * base.cache.buildTimeMultiplier)}</span>
                <span style="color:var(--dim)">${u.slots} slot${u.slots > 1 ? 's' : ''}</span></div>
            </div>
            <button class="action" data-queue="${u.id}" data-count="1"
              ${locked || !affordable ? 'disabled' : ''}>Build</button>
            <button class="action ghost" data-queue="${u.id}" data-count="5"
              ${locked ? 'disabled' : ''}>x5</button>
          </div>`;
      })
      .join('');

    return html;
  }

  // ---------------------------------------------------------------- research

  private renderResearch(state: GameState, viewerId: number): string {
    const player = state.players.find((p) => p.id === viewerId);
    if (!player) return '';

    let html = '<div class="section-title">Army-wide research</div>';
    if (player.researching) {
      const def = RESEARCH.find((r) => r.id === player.researching!.id);
      const total = researchTime(def!, player.researching.targetLevel);
      const pct = Math.max(0, Math.min(1, 1 - player.researching.remaining / total));
      html += `
        <div class="row" style="border-color:var(--border)">
          <div class="grow">
            <div class="name">${escapeHtml(def?.name ?? '')} &rarr; level ${player.researching.targetLevel}</div>
            <div class="meta"><span>${formatDuration(player.researching.remaining)} remaining</span></div>
            <div class="progress"><div style="width:${(pct * 100).toFixed(1)}%"></div></div>
          </div>
        </div>`;
    }

    html += RESEARCH.map((def) => {
      const level = player.research[def.id] ?? 0;
      const target = level + 1;
      const maxed = target > def.maxLevel;
      const cost = researchCost(def, Math.min(target, def.maxLevel));
      const affordable = canAfford(player.resources, cost);
      const busy = player.researching !== null;
      return `
        <div class="row">
          <span class="lvl">${level}</span>
          <div class="grow">
            <div class="name">${escapeHtml(def.name)}</div>
            <div class="meta"><span>${escapeHtml(def.description)}</span></div>
            <div class="cost">${this.costHtml(cost, player.resources)}
              <span style="color:var(--dim)">${formatDuration(researchTime(def, Math.min(target, def.maxLevel)))}</span></div>
          </div>
          <button class="action" data-research="${def.id}"
            ${maxed || !affordable || busy ? 'disabled' : ''}>${maxed ? 'Max' : `Lv ${target}`}</button>
        </div>`;
    }).join('');

    return html;
  }

  // ------------------------------------------------------------------- intel

  private renderIntel(state: GameState, viewerId: number): string {
    const viewer = state.players.find((p) => p.id === viewerId);
    if (!viewer) return '';

    let html = '<div class="section-title">Your bases</div>';
    const mine = state.bases.filter((b) => b.owner === viewerId);
    html += mine
      .map((b) => {
        const alive = b.buildings.filter((x) => x.level > 0 && !x.disabled).length;
        return `
          <div class="row" data-base="${b.id}" style="cursor:pointer">
            <div class="grow">
              <div class="name">${escapeHtml(b.name)}</div>
              <div class="meta">
                <span>${escapeHtml(BASE_TYPE_BY_KIND[b.kind].name)}</span>
                <span>${alive} structures</span>
                <span>${b.queue.length} queued</span>
              </div>
            </div>
          </div>`;
      })
      .join('');

    html += '<div class="section-title" style="margin-top:16px">Factions</div>';
    html += state.players
      .filter((p) => p.id !== viewerId)
      .map((p) => {
        const relation = p.team === viewer.team ? 'Ally' : 'Hostile';
        const bases = state.bases.filter((b) => b.owner === p.id).length;
        const units = state.units.filter((u) => u.owner === p.id).length;
        return `
          <div class="row">
            <span class="lvl" style="background:${p.colour}33;color:${p.colour}">&#9632;</span>
            <div class="grow">
              <div class="name">${escapeHtml(p.name)}${p.defeated ? ' <span class="tag">eliminated</span>' : ''}</div>
              <div class="meta">
                <span style="color:${p.team === viewer.team ? 'var(--good)' : 'var(--bad)'}">${relation}</span>
                <span>${bases} bases</span>
                <span>${units} units</span>
              </div>
            </div>
          </div>`;
      })
      .join('');

    html += '<div class="section-title" style="margin-top:16px">Territories</div>';
    const owned = state.territories.filter((t) => t.owner === viewerId).length;
    html += `<div class="row"><div class="grow"><div class="meta">
      <span>${owned} of ${state.territories.length} controlled</span></div></div></div>`;

    return html;
  }

  /** Cost chips, greyed red when the player cannot afford that component. */
  private costHtml(cost: Resources, have: Resources): string {
    const parts: string[] = [];
    if (cost.steel > 0) {
      parts.push(`<span class="c-steel ${have.steel < cost.steel ? 'short' : ''}">${formatNumber(cost.steel)} St</span>`);
    }
    if (cost.aluminum > 0) {
      parts.push(`<span class="c-alu ${have.aluminum < cost.aluminum ? 'short' : ''}">${formatNumber(cost.aluminum)} Al</span>`);
    }
    if (cost.fuel > 0) {
      parts.push(`<span class="c-fuel ${have.fuel < cost.fuel ? 'short' : ''}">${formatNumber(cost.fuel)} Fu</span>`);
    }
    return parts.join('');
  }
}
