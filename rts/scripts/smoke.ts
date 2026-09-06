/**
 * Headless simulation smoke test.
 *
 * Runs a full match at high speed with no renderer attached. This catches logic
 * errors, NaN propagation and crashes that a browser session might take many
 * minutes of real time to surface.
 */
import { createMatch } from '../src/sim/world';
import { Simulation } from '../src/sim/loop';
import { createOpponents } from '../src/ai/opponent';
import { UNIT_BY_ID, UNITS } from '../src/data/units';

const match = createMatch({ seed: 12345, playerName: 'Test', enemies: 2, allies: 1 });
const sim = new Simulation(match.state, match.map);
sim.setSpeed('blitz');
// Every player is AI-controlled so the match actually progresses.
for (const p of match.state.players) p.isHuman = false;
const ais = createOpponents(match.state, 'general', 4242);

console.log(`units=${UNITS.length} bases=${match.state.bases.length} territories=${match.state.territories.length}`);
console.log(`sites=${match.map.sites.length} decorations=${match.map.decorations.length}`);

const t0 = Date.now();
for (let frame = 0; frame < 3000; frame++) {
  for (const ai of ais) {
    for (const cmd of ai.update(sim.state, sim.map)) sim.enqueue(cmd);
  }
  sim.advance(1 / 30);
  if (sim.state.winner !== null) break;
}
const ms = Date.now() - t0;

// Validate that nothing has gone numerically wrong.
let problems = 0;
for (const p of sim.state.players) {
  for (const [k, v] of Object.entries(p.resources)) {
    if (!Number.isFinite(v) || v < -1) { console.error(`BAD resource ${p.name}.${k}=${v}`); problems++; }
  }
}
for (const u of sim.state.units) {
  if (!Number.isFinite(u.x) || !Number.isFinite(u.y) || !Number.isFinite(u.hp)) {
    console.error(`BAD unit ${u.defId} x=${u.x} y=${u.y} hp=${u.hp}`); problems++;
  }
  if (!UNIT_BY_ID[u.defId]) { console.error(`BAD defId ${u.defId}`); problems++; }
}
for (const b of sim.state.bases) {
  for (const bd of b.buildings) {
    if (!Number.isFinite(bd.hp)) { console.error(`BAD building hp ${bd.defId}`); problems++; }
  }
}

console.log(`simulated ${sim.state.tick} ticks (${Math.round(sim.state.time)}s game time) in ${ms}ms`);
console.log(`units=${sim.state.units.length} bases=${sim.state.bases.length} winner=${sim.state.winner}`);
for (const p of sim.state.players) {
  const bases = sim.state.bases.filter((b) => b.owner === p.id).length;
  const units = sim.state.units.filter((u) => u.owner === p.id).length;
  const terr = sim.state.territories.filter((t) => t.owner === p.id).length;
  console.log(`  ${p.name}: ${bases} bases, ${units} units, ${terr} territories, defeated=${p.defeated}`);
}
const b0 = sim.state.bases[0];
console.log('base0 buildings:', b0.buildings.map((x) => `${x.defId}=${x.level}`).join(' '));
console.log('base0 queue:', b0.queue.length, 'garrison:', JSON.stringify(b0.garrison));
console.log('base0 income:', JSON.stringify(b0.cache.income), 'cmdLvl', b0.cache.commandLevel);
console.log(sim.state.log.slice(-6).map((l) => `  [log] ${l.text}`).join('\n'));
console.log(problems === 0 ? 'SMOKE PASS' : `SMOKE FAIL (${problems} problems)`);
process.exit(problems === 0 ? 0 : 1);
