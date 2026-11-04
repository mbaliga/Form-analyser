import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const source = await fs.readFile(new URL('../../web-viewer/report.js', import.meta.url), 'utf8');
const context = vm.createContext({ TextDecoder });
vm.runInContext(`${source}\nglobalThis.summarizeReport = CrocodylReport.summarize;`, context);

const element = () => ({
  addEventListener() {},
  classList: { add() {}, remove() {} },
  click() {},
  files: [],
  hidden: false,
  textContent: '',
});
const dom = new Map([
  ['#file', element()],
  ['#dropzone', element()],
  ['#status', element()],
  ['#report', element()],
]);
const renderContext = vm.createContext({
  document: { querySelector: selector => dom.get(selector) },
  CrocodylReport: { summarize() {} },
});
const viewerSource = await fs.readFile(new URL('../../web-viewer/viewer.js', import.meta.url), 'utf8');
vm.runInContext(`${viewerSource}\nglobalThis.renderAnalysisForTest = renderAnalysis;`, renderContext);

const encode = value => new TextEncoder().encode(JSON.stringify(value));
const sessions = [
  { id: 'form-1', startedAtEpochMs: 1_000, distanceMeters: 70, durationS: 3_600, arrowsActual: 6 },
  { id: 'form-2', startedAtEpochMs: 2_000, distanceMeters: 18, durationAutoS: 900, arrowsActual: 6 },
  { id: 'retracted', startedAtEpochMs: 3_000, deletedAt: 3_500 },
];
const shots = [
  { sessionId: 'form-1', indexInSession: 0, featuresJson: '{"bow_arm_angle":170,"release_s":0.25}', score: 8, isBaseline: true },
  { sessionId: 'form-1', indexInSession: 1, featuresJson: '{"bow_arm_angle":172,"release_s":0.35}', score: 9, isBaseline: true },
  { sessionId: 'form-2', indexInSession: 0, featuresJson: '{"bow_arm_angle":173,"release_s":0.45}', score: 10, isBaseline: false },
  { sessionId: 'retracted', indexInSession: 0, featuresJson: '{"bow_arm_angle":999}', score: 10, isBaseline: false },
  { sessionId: 'form-2', indexInSession: 1, featuresJson: '{broken', score: null, isBaseline: false },
];
const scoreSessions = Array.from({ length: 8 }, (_, index) => ({
  id: `round-${index}`,
  roundName: `Round ${index}`,
  startedAt: index * 1_000,
  completedAt: index * 1_000 + 500,
  updatedAt: index * 1_000 + 500,
  status: 'FINISHED',
  roundComplete: 1,
  scoringKind: 'CUMULATIVE',
  arrowsPerEnd: 6,
  endCount: 6,
  total: 300 + index * 5,
  xCount: index,
}));
scoreSessions.push({
  id: 'set-match', roundName: 'Match', status: 'FINISHED', roundComplete: 1,
  scoringKind: 'SET_MATCH', arrowsPerEnd: 3, endCount: 5, total: 6, xCount: 0,
});
scoreSessions.push({
  id: 'retracted-round', roundName: 'Removed', status: 'FINISHED', roundComplete: 1,
  scoringKind: 'CUMULATIVE', arrowsPerEnd: 6, endCount: 6, total: 360, deletedAt: 1,
});

const analysis = context.summarizeReport({
  'tables/session.json': encode(sessions),
  'tables/shot.json': encode(shots),
  'tables/score_session.json': encode(scoreSessions),
});

assert.deepEqual(JSON.parse(JSON.stringify(analysis.totals)), {
  formSessions: 2,
  formShots: 4,
  scoredShots: 3,
  baselineShots: 2,
  completedRounds: 9,
  averageShotScore: 9,
});
assert.equal(analysis.formSessions[0].id, 'form-2');
assert.equal(analysis.formSessions[0].duration, 900);
assert.equal(analysis.formSessions[0].shotCount, 2);
const setMatch = analysis.rounds.find(round => round.id === 'set-match');
assert.equal(setMatch.name, 'Match');
assert.equal(setMatch.percentage, null, 'set-match points must not be misrepresented as target score percentage');
assert.equal(analysis.rounds.length, 9, 'retracted score sessions must be excluded');
assert.equal(analysis.roundTrend.previousCount, 3);
assert.equal(analysis.roundTrend.recentCount, 5);
const bowArm = analysis.baselineFeatures.find(feature => feature.name === 'bow_arm_angle');
assert.equal(bowArm.baselineCount, 2);
assert.equal(bowArm.mean, 171);
assert.ok(Math.abs(bowArm.zScore - Math.sqrt(2)) < 1e-9);
assert.equal(analysis.latestBaselineAt, 2_000);

const empty = context.summarizeReport({});
assert.equal(empty.totals.formSessions, 0);
assert.equal(empty.totals.completedRounds, 0);
assert.deepEqual(JSON.parse(JSON.stringify(empty.baselineFeatures)), []);

const rendered = renderContext.renderAnalysisForTest({
  totals: {
    formSessions: 1, formShots: 1, scoredShots: 1, averageShotScore: 9,
    baselineShots: 1, completedRounds: 1,
  },
  rounds: [{
    id: 'round', at: 1_000, name: '<script>alert(1)</script>', total: 9,
    maximum: null, percentage: null, xCount: 0, scoringKind: 'SET_MATCH',
  }],
  formSessions: [],
  baselineFeatures: [{
    name: '<img src=x onerror=alert(1)>', baselineCount: 1, mean: 1,
    standardDeviation: null, latest: 2, zScore: null,
  }],
  roundTrend: { previousCount: 0, recentCount: 1, previousAverage: null, recentAverage: null },
});
assert.ok(!rendered.includes('<script>alert(1)</script>'), 'round names must be escaped before HTML rendering');
assert.ok(rendered.includes('&lt;script&gt;alert(1)&lt;/script&gt;'));
assert.ok(!rendered.includes('<img src=x onerror=alert(1)>'), 'feature names must be escaped before HTML rendering');
assert.ok(rendered.includes('Set match'), 'set-match score points must remain distinct in the report');
assert.ok(!rendered.includes('9.0%'), 'set-match points must not render as a cumulative score percentage');
console.log('Offline report analysis passed');
