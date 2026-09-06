import type { MatchSetup } from '../sim/world';
import type { Difficulty } from '../ai/opponent';
import { SPEED_OPTIONS, DEFAULT_SPEED } from '../data/config';
import type { SpeedPreset } from '../data/config';

/**
 * The pre-match setup screen.
 *
 * Deliberately short: name, opposition, difficulty, speed. Everything else is
 * discoverable in game rather than buried in a settings wall.
 */

export interface SetupResult extends MatchSetup {
  difficulty: Difficulty;
  speed: SpeedPreset;
}

export function showSetup(parent: HTMLElement): Promise<SetupResult> {
  return new Promise((resolve) => {
    const overlay = document.createElement('div');
    overlay.className = 'overlay';

    let difficulty: Difficulty = 'officer';
    let speed: SpeedPreset = DEFAULT_SPEED;
    let enemies = 2;
    let allies = 1;

    overlay.innerHTML = `
      <div class="dialog panel">
        <h1>Desert <span>Order</span></h1>
        <p class="sub">
          A large-scale isometric war of industry and armour. Build your economy, claim
          territory, field the right counters, and break the enemy command structure.
        </p>

        <div class="field">
          <span class="label">Commander name</span>
          <input type="text" id="s-name" value="Commander" maxlength="24" />
        </div>

        <div class="field">
          <span class="label">Opposition</span>
          <div class="choice-row" id="s-enemies">
            <button data-v="1">1 enemy</button>
            <button data-v="2" class="active">2 enemies</button>
            <button data-v="3">3 enemies</button>
            <button data-v="5">5 enemies</button>
          </div>
        </div>

        <div class="field">
          <span class="label">Allies</span>
          <div class="choice-row" id="s-allies">
            <button data-v="0">Alone</button>
            <button data-v="1" class="active">1 ally</button>
            <button data-v="2">2 allies</button>
          </div>
        </div>

        <div class="field">
          <span class="label">AI difficulty</span>
          <div class="choice-row" id="s-diff">
            <button data-v="recruit">Recruit</button>
            <button data-v="officer" class="active">Officer</button>
            <button data-v="general">General</button>
          </div>
        </div>

        <div class="field">
          <span class="label">Game speed</span>
          <div class="choice-row" id="s-speed">
            ${SPEED_OPTIONS.map(
              (o) =>
                `<button data-v="${o.id}" class="${o.id === DEFAULT_SPEED ? 'active' : ''}"
                   title="${o.description}">${o.label}</button>`,
            ).join('')}
          </div>
        </div>

        <button class="primary" id="s-start">Deploy</button>

        <div class="hint">
          <b>Left-drag</b> to select units &middot; <b>Right-click</b> to move or attack &middot;
          <b>Right-drag</b> or <b>WASD</b> to pan &middot; <b>Scroll</b> to zoom &middot;
          <b>Space</b> to pause<br>
          Units are 5x stronger against their specialisation and 5x weaker against
          everything else, so mixed armies beat monolithic ones.
        </div>
      </div>`;

    parent.appendChild(overlay);

    const group = (id: string, onPick: (value: string) => void) => {
      const el = overlay.querySelector(`#${id}`);
      el?.querySelectorAll('button').forEach((b) => {
        b.addEventListener('click', () => {
          el.querySelectorAll('button').forEach((x) => x.classList.remove('active'));
          b.classList.add('active');
          onPick(b.dataset.v ?? '');
        });
      });
    };

    group('s-enemies', (v) => (enemies = Number(v)));
    group('s-allies', (v) => (allies = Number(v)));
    group('s-diff', (v) => (difficulty = v as Difficulty));
    group('s-speed', (v) => (speed = v as SpeedPreset));

    overlay.querySelector('#s-start')?.addEventListener('click', () => {
      const nameInput = overlay.querySelector<HTMLInputElement>('#s-name');
      const playerName = (nameInput?.value || 'Commander').trim();
      overlay.remove();
      resolve({
        seed: Math.floor(Math.random() * 0x7fffffff),
        playerName,
        enemies,
        allies,
        difficulty,
        speed,
      });
    });
  });
}

/** End-of-match banner. */
export function showResult(parent: HTMLElement, won: boolean, onRestart: () => void): void {
  const overlay = document.createElement('div');
  overlay.className = 'overlay';
  overlay.innerHTML = `
    <div class="dialog panel" style="text-align:center">
      <h1>${won ? '<span>Victory</span>' : 'Defeat'}</h1>
      <p class="sub" style="margin-bottom:28px">
        ${
          won
            ? 'Every hostile command structure has been reduced. The theatre is yours.'
            : 'Your command structure has collapsed. The desert belongs to someone else.'
        }
      </p>
      <button class="primary" id="r-again">New campaign</button>
    </div>`;
  parent.appendChild(overlay);
  overlay.querySelector('#r-again')?.addEventListener('click', () => {
    overlay.remove();
    onRestart();
  });
}
