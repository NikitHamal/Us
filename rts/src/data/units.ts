import type { UnitDef, Specialisation, BaseKind, Domain } from './types';

/**
 * The full unit roster, transcribed from the Desert Order stat table.
 *
 * Steel/aluminum/damage/armor/range/fuel are taken verbatim from the source data so
 * the balance curve is the real one. Two columns the original table does not expose --
 * hp and speed -- are derived rather than invented: hp scales off armor and cost so a
 * King Tiger genuinely soaks more than a Luchs, and speed is assigned per weight class.
 * Build time is derived from total cost so the economy stays self-consistent as new
 * units are added.
 */

interface Row {
  id: string;
  name: string;
  domain: Domain;
  steel: number;
  aluminum: number;
  damage: number;
  armor: number;
  range: number;
  fuel: number;
  spec?: Specialisation[];
  mult?: number;
  at: BaseKind;
  /** Weight class drives speed and hp multipliers. */
  weight: 'light' | 'medium' | 'heavy' | 'super';
  desc: string;
}

const M = 1_000_000;

// ---------------------------------------------------------------- ground
const GROUND: Row[] = [
  { id: 'conquest_truck', name: 'Conquest Truck', domain: 'ground', steel: 45000, aluminum: 5000, damage: 0, armor: 3, range: 0, fuel: 20, spec: ['apc'], at: 'tank', weight: 'light', desc: 'Unarmed APC. Required to claim and hold captured territory.' },
  { id: 'm16_mgmc', name: 'M16 MGMC', domain: 'ground', steel: 45000, aluminum: 5000, damage: 200, armor: 7, range: 5, fuel: 10, spec: ['vs_air'], mult: 2, at: 'tank', weight: 'light', desc: 'Quad-fifty halftrack. Doubles range against aircraft.' },
  { id: 'luchs', name: 'SDKFZ123 Luchs', domain: 'ground', steel: 35000, aluminum: 5000, damage: 100, armor: 15, range: 8, fuel: 15, at: 'tank', weight: 'light', desc: 'Fast reconnaissance tank. Cheap early screening unit.' },
  { id: 'calliope_willys', name: 'Willys MB Calliope', domain: 'ground', steel: 65000, aluminum: 5000, damage: 500, armor: 7, range: 8, fuel: 5, mult: 2, at: 'tank', weight: 'light', desc: 'Rocket jeep. Fast, and engages at double range.' },
  { id: 'katyusha', name: 'BM-13 Katyusha', domain: 'ground', steel: 850000, aluminum: 750000, damage: 5000, armor: 10, range: 10, fuel: 25, mult: 3, at: 'tank', weight: 'medium', desc: 'Rocket artillery. Triple range engagement, devastating salvos.' },
  { id: 'sherman', name: 'M4 Sherman Heavy tank', domain: 'ground', steel: 450000, aluminum: 200000, damage: 1500, armor: 150, range: 10, fuel: 50, at: 'tank', weight: 'heavy', desc: 'Reliable mainline heavy tank.' },
  { id: 'sp6_howitzer', name: 'SP6 Sturmtiger Howitzer', domain: 'ground', steel: 1.97 * M, aluminum: 900000, damage: 20000, armor: 1875, range: 12, fuel: 75, spec: ['vs_bases'], at: 'tank', weight: 'super', desc: 'Siege howitzer. Levels bases, useless against mobile targets.' },
  { id: 'maultier', name: 'SDKFZ4 Maultier', domain: 'ground', steel: 375000, aluminum: 250000, damage: 950, armor: 15, range: 10, fuel: 30, mult: 2, at: 'tank', weight: 'medium', desc: 'Halftrack rocket launcher with extended reach.' },
  { id: 'breda501', name: 'Breda 501', domain: 'ground', steel: 95000, aluminum: 35000, damage: 250, armor: 30, range: 8, fuel: 15, spec: ['vs_bases'], at: 'tank', weight: 'light', desc: 'Light self-propelled gun for early base pressure.' },
  { id: 'semovente', name: '90/53 su Lancia 3Ro', domain: 'ground', steel: 390000, aluminum: 15000, damage: 1000, armor: 30, range: 10, fuel: 25, spec: ['vs_bases'], at: 'tank', weight: 'medium', desc: 'Truck-mounted 90mm. Long-range base breaker.' },
  { id: 'tetrarch', name: 'Tetrarch LT Mk VII', domain: 'ground', steel: 500000, aluminum: 15000, damage: 750, armor: 8, range: 8, fuel: 35, spec: ['vs_trains'], at: 'tank', weight: 'light', desc: 'Air-portable light tank specialised against rail units.' },
  { id: 'leopard_lt', name: 'VK1602 Leopard LT', domain: 'ground', steel: 310000, aluminum: 120000, damage: 150, armor: 75, range: 6, fuel: 45, spec: ['vs_boats'], mult: 3, at: 'tank', weight: 'medium', desc: 'Coastal-defence light tank. Triple range against boats.' },
  { id: 'm3_stuart', name: 'M3 Stuart', domain: 'ground', steel: 95000, aluminum: 55000, damage: 250, armor: 25, range: 8, fuel: 35, at: 'tank', weight: 'light', desc: 'Dependable light tank, good cost-to-armor ratio.' },
  { id: 't26', name: 'T-26', domain: 'ground', steel: 590000, aluminum: 90000, damage: 250, armor: 150, range: 10, fuel: 50, spec: ['vs_vehicles'], at: 'tank', weight: 'medium', desc: 'Infantry tank tuned to shred weak vehicles.' },
  { id: 'kv2', name: 'KW2 Kliment Woroszylowa', domain: 'ground', steel: 1.3 * M, aluminum: 600000, damage: 6000, armor: 1500, range: 9, fuel: 80, spec: ['vs_vehicles'], mult: 2, at: 'tank', weight: 'heavy', desc: 'Massive howitzer turret. Double range on vehicle targets.' },
  { id: 'jagdpanther', name: 'SDKFZ173 Jagdpanther', domain: 'ground', steel: 1.3 * M, aluminum: 500000, damage: 800, armor: 300, range: 9, fuel: 80, spec: ['vs_bases'], at: 'tank', weight: 'heavy', desc: 'Tank destroyer repurposed as a base-attack platform.' },
  { id: 't34_85', name: 'T-34-85', domain: 'ground', steel: 2.5 * M, aluminum: 750000, damage: 400, armor: 600, range: 8, fuel: 50, spec: ['vs_vehicles', 'vs_boats'], mult: 3, at: 'tank', weight: 'heavy', desc: 'Versatile medium tank, triple range against vehicles and boats.' },
  { id: 'sig33_1', name: 'sIG33 Sturmpanzer I Bison', domain: 'ground', steel: 135000, aluminum: 105000, damage: 2500, armor: 30, range: 6, fuel: 40, spec: ['vs_bases'], at: 'tank', weight: 'light', desc: 'Early assault gun. Cheap siege damage.' },
  { id: 'sig33_2', name: 'sIG33 Sturmpanzer II Bison', domain: 'ground', steel: 390000, aluminum: 290000, damage: 1750, armor: 50, range: 10, fuel: 45, spec: ['vs_bases'], at: 'tank', weight: 'medium', desc: 'Improved Bison with longer reach.' },
  { id: 'wespe', name: 'Wespe Howitzer', domain: 'ground', steel: 150000, aluminum: 35000, damage: 1200, armor: 75, range: 10, fuel: 40, spec: ['vs_bases'], at: 'tank', weight: 'light', desc: 'Light mobile howitzer for harassing structures.' },
  { id: 'sturmpanzer4', name: 'SDKFZ166 Sturmpanzer IV', domain: 'ground', steel: 2.9 * M, aluminum: 1.9 * M, damage: 2250, armor: 300, range: 10, fuel: 65, spec: ['vs_bases'], at: 'tank', weight: 'heavy', desc: 'Brummbar assault howitzer. Heavy siege backbone.' },
  { id: 'wirbelwind', name: 'FP6 Flakpanzer IV Wirbelwind', domain: 'ground', steel: 950000, aluminum: 1 * M, damage: 375, armor: 100, range: 10, fuel: 60, spec: ['vs_air'], mult: 2, at: 'tank', weight: 'medium', desc: 'Quad-20mm flakpanzer. Mobile anti-air umbrella.' },
  { id: 'tiger1', name: 'P6 Tiger I Heavy tank', domain: 'ground', steel: 1.9 * M, aluminum: 650000, damage: 2500, armor: 1500, range: 10, fuel: 75, at: 'tank', weight: 'heavy', desc: 'The classic heavy. Thick armor, punishing gun.' },
  { id: 't34_calliope', name: 'T34 Calliope Heavy tank', domain: 'ground', steel: 950000, aluminum: 250000, damage: 1500, armor: 150, range: 10, fuel: 50, mult: 2, at: 'tank', weight: 'heavy', desc: 'Rocket-rack Sherman. Doubles engagement range.' },
  { id: 'saint_chamond', name: 'GPFSaintChamond', domain: 'ground', steel: 975000, aluminum: 1 * M, damage: 14000, armor: 37, range: 7, fuel: 50, spec: ['vs_bases'], mult: 3, at: 'tank', weight: 'heavy', desc: 'Railway-gun derivative. Enormous damage, paper armor.' },
  { id: 'cromwell', name: 'A27M Cruiser MkIV Cromwell', domain: 'ground', steel: 650000, aluminum: 650000, damage: 2000, armor: 250, range: 9, fuel: 75, at: 'tank', weight: 'medium', desc: 'Fast cruiser tank. Excellent flanker.' },
  { id: 'is3', name: 'IS-3M Stalin Tankograd', domain: 'ground', steel: 8.9 * M, aluminum: 3.9 * M, damage: 3000, armor: 3000, range: 10, fuel: 110, at: 'tank', weight: 'super', desc: 'Late-game breakthrough heavy. Balanced and brutal.' },
  { id: 'king_tiger', name: 'P6 Tiger II King Heavy', domain: 'ground', steel: 9.5 * M, aluminum: 5.5 * M, damage: 2500, armor: 4166, range: 10, fuel: 100, at: 'tank', weight: 'super', desc: 'Konigstiger. The toughest conventional tank in the roster.' },
  { id: 'm4a1_skink', name: 'M4A1 Skink', domain: 'ground', steel: 750000, aluminum: 175000, damage: 700, armor: 150, range: 8, fuel: 50, spec: ['vs_aircraft'], mult: 2, at: 'tank', weight: 'medium', desc: 'Quad-20mm AA turret on a Sherman hull.' },
  { id: 'm19_mgmc', name: 'M19 MGMC', domain: 'ground', steel: 875000, aluminum: 225000, damage: 3000, armor: 50, range: 9, fuel: 40, spec: ['vs_aircraft'], mult: 2, at: 'tank', weight: 'medium', desc: 'Twin-40mm Bofors carriage. Shreds big aircraft.' },
  { id: 'nashorn', name: 'SDKFZ164 Nashorn', domain: 'ground', steel: 900000, aluminum: 300000, damage: 650, armor: 93, range: 10, fuel: 40, spec: ['vs_vehicles'], mult: 2, at: 'tank', weight: 'medium', desc: '88mm tank hunter with doubled engagement range.' },
  { id: 'elefant', name: 'SDKFZ184 Elefant', domain: 'ground', steel: 13.9 * M, aluminum: 12.9 * M, damage: 675, armor: 4491, range: 10, fuel: 90, spec: ['vs_vehicles'], mult: 2, at: 'tank', weight: 'super', desc: 'Ferdinand casemate destroyer. Nearly immovable armor.' },
  { id: 'su122', name: 'SU-122 (T-34)', domain: 'ground', steel: 950000, aluminum: 390000, damage: 2250, armor: 500, range: 10, fuel: 75, spec: ['vs_trains'], at: 'tank', weight: 'medium', desc: 'Assault gun specialised against rail formations.' },
  { id: 'b4_howitzer', name: '203mm Howitzer M1931 (B-4)', domain: 'ground', steel: 1.39 * M, aluminum: 1.69 * M, damage: 12500, armor: 500, range: 10, fuel: 55, spec: ['vs_bases'], mult: 3, at: 'tank', weight: 'heavy', desc: 'Stalin\u2019s Sledgehammer. Triple-range siege artillery.' },
  { id: 't28_super', name: 'T28 Super Heavy tank', domain: 'ground', steel: 9.5 * M, aluminum: 4.5 * M, damage: 1000, armor: 7500, range: 10, fuel: 100, spec: ['vs_vehicles'], mult: 2, at: 'tank', weight: 'super', desc: 'Super-heavy gun carriage. Absorbs enormous punishment.' },
  { id: 'maus', name: 'SDKFZ205 Maus Super Heavy', domain: 'ground', steel: 11 * M, aluminum: 3 * M, damage: 5000, armor: 5000, range: 9, fuel: 125, spec: ['vs_vehicles', 'vs_trains'], mult: 2, at: 'tank', weight: 'super', desc: 'The heaviest tank ever built. Slow, unstoppable.' },
  { id: 'karl_geraet', name: 'Karl-Geraet 041 Howitzer', domain: 'ground', steel: 9 * M, aluminum: 8 * M, damage: 100000, armor: 750, range: 12, fuel: 150, spec: ['vs_bases'], mult: 3, at: 'tank', weight: 'super', desc: 'Siege mortar. Single shots erase entire structures.' },
  { id: 'mp4_karl', name: 'MP4 Karl Ammunition Support', domain: 'ground', steel: 1.5 * M, aluminum: 500000, damage: 0, armor: 75, range: 0, fuel: 50, spec: ['support'], at: 'tank', weight: 'medium', desc: 'Resupplies Karl-Geraet. Unarmed, keep it behind the line.' },
  { id: 'mp1_ammo', name: 'MP1 Heavy Ammunition Support', domain: 'ground', steel: 750000, aluminum: 0, damage: 0, armor: 18, range: 0, fuel: 30, spec: ['support'], at: 'tank', weight: 'light', desc: 'Generic ammunition carrier for heavy artillery.' },
];

// ---------------------------------------------------------------- copters
const COPTERS: Row[] = [
  { id: 'sikorsky_r4', name: 'Sikorsky-R4 copter', domain: 'air', steel: 25000, aluminum: 225000, damage: 500, armor: 30, range: 8, fuel: 125, at: 'helicopter', weight: 'light', desc: 'First production helicopter. Cheap aerial scout.' },
  { id: 'fw61', name: 'Focke-Wulf-FW-61 copter', domain: 'air', steel: 100000, aluminum: 1.25 * M, damage: 2000, armor: 150, range: 8, fuel: 175, spec: ['vs_vehicles'], at: 'helicopter', weight: 'medium', desc: 'Gunship tuned for armour hunting.' },
  { id: 'sikorsky_h5', name: 'Sikorsky-H5 copter', domain: 'air', steel: 200000, aluminum: 1.75 * M, damage: 5500, armor: 375, range: 4, fuel: 225, spec: ['vs_bases'], at: 'helicopter', weight: 'medium', desc: 'Heavy-lift bomb-carrier. Short reach, huge structural damage.' },
  { id: 'flettner_fl265', name: 'Flettner-FL-265 copter', domain: 'air', steel: 95000, aluminum: 750000, damage: 900, armor: 75, range: 10, fuel: 125, spec: ['vs_air'], at: 'helicopter', weight: 'medium', desc: 'Intermeshing-rotor interceptor. Owns the low airspace.' },
  { id: 'platt_lepage', name: 'Platt-LePage XR-1 RAM copter', domain: 'air', steel: 390000, aluminum: 2.95 * M, damage: 2500, armor: 150, range: 10, fuel: 275, spec: ['stealth'], at: 'helicopter', weight: 'heavy', desc: 'Stealth rotorcraft. Invisible without enemy detectors.' },
];

// ---------------------------------------------------------------- planes
const PLANES: Row[] = [
  { id: 'il2', name: 'IL2 Iljuschin Schturmowik', domain: 'air', steel: 100000, aluminum: 750000, damage: 600, armor: 250, range: 5, fuel: 150, spec: ['vs_vehicles'], at: 'air', weight: 'medium', desc: 'Flying tank. Armoured ground-attack aircraft.' },
  { id: 'ki48', name: 'Kawasaki KI-48 Sokei Bomber', domain: 'air', steel: 450000, aluminum: 4.5 * M, damage: 7500, armor: 1000, range: 5, fuel: 750, spec: ['vs_bases'], at: 'air', weight: 'heavy', desc: 'Twin-engine bomber for structural demolition.' },
  { id: 'i15', name: 'Polikarpov I15 plane', domain: 'air', steel: 55000, aluminum: 725000, damage: 100, armor: 50, range: 12, fuel: 150, spec: ['vs_copters'], at: 'air', weight: 'light', desc: 'Biplane fighter. Cheap counter to helicopter swarms.' },
  { id: 'spitfire', name: 'Supermarine Spitfire', domain: 'air', steel: 250000, aluminum: 800000, damage: 500, armor: 375, range: 12, fuel: 200, spec: ['vs_aircraft'], at: 'air', weight: 'medium', desc: 'Air-superiority fighter. Excellent interceptor.' },
  { id: 'p38', name: 'Lockheed P38 Lightning', domain: 'air', steel: 2.5 * M, aluminum: 8.5 * M, damage: 3000, armor: 3750, range: 12, fuel: 550, spec: ['vs_aircraft'], at: 'air', weight: 'heavy', desc: 'Twin-boom heavy fighter. Dominates contested skies.' },
  { id: 'me262', name: 'ME262 Messerschmitt Jet', domain: 'air', steel: 750000, aluminum: 2.9 * M, damage: 2500, armor: 750, range: 12, fuel: 400, spec: ['vs_aircraft'], at: 'air', weight: 'heavy', desc: 'First operational jet fighter. Blisteringly fast.' },
  { id: 'b25', name: 'B25 Mitchell Bomber', domain: 'air', steel: 750000, aluminum: 4.5 * M, damage: 5000, armor: 3000, range: 6, fuel: 700, spec: ['vs_boats'], at: 'air', weight: 'heavy', desc: 'Medium bomber configured for anti-shipping strikes.' },
  { id: 'ju87', name: 'Junkers JU87 Stuka Nachtrevi', domain: 'air', steel: 900000, aluminum: 1 * M, damage: 3000, armor: 375, range: 8, fuel: 400, spec: ['stealth'], at: 'air', weight: 'medium', desc: 'Night-raider Stuka. Stealthy precision dive bomber.' },
  { id: 'bf110', name: 'BF110 Messerschmitt', domain: 'air', steel: 490000, aluminum: 4.9 * M, damage: 900, armor: 2500, range: 10, fuel: 800, spec: ['detector'], at: 'air', weight: 'heavy', desc: 'Radar-equipped heavy fighter. Reveals stealth units.' },
  { id: 'halifax', name: 'Handley Page Halifax Bomber', domain: 'air', steel: 1.99 * M, aluminum: 13.9 * M, damage: 70000, armor: 3750, range: 6, fuel: 900, spec: ['vs_bases'], at: 'air', weight: 'super', desc: 'Four-engine heavy bomber. Flattens fortified bases.' },
  { id: 'ho229', name: 'Horten Ho 229 v7 Bomber', domain: 'air', steel: 1.9 * M, aluminum: 19 * M, damage: 2000, armor: 2000, range: 6, fuel: 2500, spec: ['vs_bases', 'stealth'], at: 'air', weight: 'super', desc: 'Flying-wing stealth bomber. Strikes before detection.' },
  { id: 'ki30', name: 'Mitsubishi Ki-30 plane', domain: 'air', steel: 220000, aluminum: 2.2 * M, damage: 2000, armor: 500, range: 6, fuel: 300, spec: ['vs_trains'], at: 'air', weight: 'medium', desc: 'Light bomber specialised against rail traffic.' },
  { id: 'ju88', name: 'Junkers JU88 plane', domain: 'air', steel: 500000, aluminum: 3.5 * M, damage: 12500, armor: 3000, range: 5, fuel: 300, spec: ['vs_vehicles'], at: 'air', weight: 'heavy', desc: 'Multirole bomber. Enormous anti-vehicle payload.' },
  { id: 'he51', name: 'Heinkel HE51 plane', domain: 'air', steel: 50000, aluminum: 1 * M, damage: 1000, armor: 250, range: 10, fuel: 175, at: 'air', weight: 'light', desc: 'Obsolete biplane. Cheap disposable escort.' },
];

// ---------------------------------------------------------------- naval
const NAVAL: Row[] = [
  { id: 'conquest_boat', name: 'Conquest boat', domain: 'naval', steel: 35000, aluminum: 1000, damage: 0, armor: 15, range: 0, fuel: 5, spec: ['apc'], at: 'harbor', weight: 'light', desc: 'Naval APC. Claims coastal and island territory.' },
  { id: 'mbk186', name: 'MBK186 Project Patrol boat', domain: 'naval', steel: 4.5 * M, aluminum: 2.5 * M, damage: 1500, armor: 7500, range: 18, fuel: 45, spec: ['vs_vehicles'], at: 'harbor', weight: 'heavy', desc: 'Armoured river gunboat. Shells the shoreline.' },
  { id: 'bk1125', name: 'BK1125 Project AA boat', domain: 'naval', steel: 1.5 * M, aluminum: 350000, damage: 3000, armor: 10, range: 18, fuel: 35, spec: ['vs_air'], at: 'harbor', weight: 'medium', desc: 'Anti-aircraft launch. Air cover for the fleet.' },
  { id: 'flower_howitzer', name: 'Flower class Howitzer boat', domain: 'naval', steel: 19 * M, aluminum: 12 * M, damage: 12000, armor: 15000, range: 18, fuel: 55, spec: ['vs_bases'], at: 'harbor', weight: 'super', desc: 'Corvette refit with siege howitzers. Coastal bombardment.' },
  { id: 'fast_attack', name: 'Fast Attack boat', domain: 'naval', steel: 45000, aluminum: 15000, damage: 100, armor: 10, range: 12, fuel: 5, at: 'harbor', weight: 'light', desc: 'Cheap fast skirmisher. Good for scouting sea lanes.' },
  { id: 'pt596', name: 'PT596 Torpedo boat', domain: 'naval', steel: 750000, aluminum: 100000, damage: 1600, armor: 750, range: 12, fuel: 15, spec: ['vs_boats'], at: 'harbor', weight: 'medium', desc: 'Torpedo runner. Specialised ship killer.' },
  { id: 'm_class_sub', name: 'M-class Submarine', domain: 'naval', steel: 3.5 * M, aluminum: 1.5 * M, damage: 2000, armor: 2500, range: 8, fuel: 90, spec: ['vs_boats', 'stealth'], mult: 2, at: 'harbor', weight: 'heavy', desc: 'Attacks boats only. Submerged and undetectable.' },
  { id: 'sumner', name: 'Allen M. Sumner class Destroyer', domain: 'naval', steel: 35 * M, aluminum: 9 * M, damage: 7500, armor: 25000, range: 16, fuel: 75, mult: 2, at: 'harbor', weight: 'super', desc: 'Fleet destroyer. The capital ship of the roster.' },
  { id: 'ammo_transport', name: 'Ammunition Transport boat', domain: 'naval', steel: 1.25 * M, aluminum: 250000, damage: 0, armor: 75, range: 0, fuel: 35, spec: ['support'], at: 'harbor', weight: 'medium', desc: 'Seaborne resupply for howitzer vessels.' },
];

// ---------------------------------------------------------------- rail
const RAIL: Row[] = [
  { id: 'locomotive', name: 'Train Locomotive', domain: 'rail', steel: 390000, aluminum: 90000, damage: 0, armor: 5, range: 0, fuel: 5, spec: ['support'], at: 'train', weight: 'medium', desc: 'Engine that pulls flatcars. Required to field any rail unit.' },
  { id: 'field_flatcar', name: 'Field Artillery Flatcar', domain: 'rail', steel: 850000, aluminum: 150000, damage: 750, armor: 500, range: 18, fuel: 10, spec: ['vs_vehicles'], at: 'train', weight: 'medium', desc: 'Flatcar-mounted field guns with extreme reach.' },
  { id: 'howitzer_flatcar', name: 'Howitzer Flatcar', domain: 'rail', steel: 4.5 * M, aluminum: 900000, damage: 12000, armor: 2500, range: 18, fuel: 25, spec: ['vs_bases'], mult: 3, at: 'train', weight: 'heavy', desc: 'Railway siege howitzer. Triple-range base destruction.' },
  { id: 'aa_flatcar', name: 'Anti-Air Flatcar', domain: 'rail', steel: 1.95 * M, aluminum: 250000, damage: 1500, armor: 3750, range: 12, fuel: 20, spec: ['vs_air'], mult: 2, at: 'train', weight: 'medium', desc: 'Mobile flak battery on rails.' },
  { id: 'panzerjagerwagen', name: 'Panzerjagerwagen', domain: 'rail', steel: 2.2 * M, aluminum: 500000, damage: 6000, armor: 750, range: 15, fuel: 20, mult: 2, at: 'train', weight: 'heavy', desc: 'Armoured rail gun car. Doubles engagement range.' },
  { id: 'triebwagen51', name: 'Panzerjaeger Triebwagen 51', domain: 'rail', steel: 3.9 * M, aluminum: 1.9 * M, damage: 3750, armor: 3750, range: 12, fuel: 15, spec: ['vs_boats', 'vs_trains'], mult: 3, at: 'train', weight: 'heavy', desc: 'Self-propelled armoured railcar. Triple range vs boats and trains.' },
];

/** Speed and hp characteristics per weight class, per domain. */
const WEIGHT_SPEED: Record<Row['weight'], number> = { light: 2.6, medium: 1.9, heavy: 1.3, super: 0.85 };
const DOMAIN_SPEED: Record<Domain, number> = { ground: 1, air: 2.4, naval: 1.15, rail: 2.0 };
const WEIGHT_HP: Record<Row['weight'], number> = { light: 220, medium: 700, heavy: 2200, super: 6000 };

/**
 * Derives HP from armor and weight class. Armor in the source table spans 3 to 25000,
 * so it is used as a soak contribution rather than raw HP, keeping early units killable
 * while late-game hulls stay meaningfully tanky.
 */
function deriveHp(row: Row): number {
  return Math.round(WEIGHT_HP[row.weight] + row.armor * 4.5);
}

function deriveBuildTime(row: Row): number {
  const total = row.steel + row.aluminum;
  // Logarithmic so a 35-million-cost destroyer is slow but not unplayable.
  return Math.round(12 + Math.pow(total / 1000, 0.62));
}

function deriveSlots(row: Row): number {
  return row.weight === 'super' ? 4 : row.weight === 'heavy' ? 3 : row.weight === 'medium' ? 2 : 1;
}

function toDef(row: Row): UnitDef {
  return {
    id: row.id,
    name: row.name,
    domain: row.domain,
    cost: { steel: row.steel, aluminum: row.aluminum },
    damage: row.damage,
    armor: row.armor,
    range: row.range,
    fuel: row.fuel,
    speed: WEIGHT_SPEED[row.weight] * DOMAIN_SPEED[row.domain],
    hp: deriveHp(row),
    specialisations: row.spec ?? ['none'],
    rangeEngageMultiplier: row.mult,
    producedAt: row.at,
    buildTime: deriveBuildTime(row),
    slots: deriveSlots(row),
    description: row.desc,
  };
}

export const UNITS: UnitDef[] = [...GROUND, ...COPTERS, ...PLANES, ...NAVAL, ...RAIL].map(toDef);

export const UNIT_BY_ID: Record<string, UnitDef> = Object.fromEntries(
  UNITS.map((u) => [u.id, u]),
);

/**
 * Home bases are not a dead end: they can field the light ground units needed to
 * bootstrap, most importantly the Conquest Truck. Without this the game deadlocks --
 * expansion needs an APC, an APC needs a Tank base, and a Tank base needs expansion.
 * Anything heavier still requires the dedicated specialised base.
 */
const HOME_BOOTSTRAP_TIER = 2;

function totalCost(u: UnitDef): number {
  return u.cost.steel + u.cost.aluminum;
}

export function homeBuildable(u: UnitDef): boolean {
  if (u.domain !== 'ground') return false;
  if (u.specialisations.includes('apc')) return true;
  return totalCost(u) < 150_000 * Math.pow(5, HOME_BOOTSTRAP_TIER - 1);
}

export function unitsForBase(kind: BaseKind): UnitDef[] {
  if (kind === 'home') return UNITS.filter(homeBuildable);
  return UNITS.filter((u) => u.producedAt === kind);
}

/** Whether a given base type may produce a given unit. */
export function canProduceAt(kind: BaseKind, u: UnitDef): boolean {
  return kind === 'home' ? homeBuildable(u) : u.producedAt === kind;
}
