/* Pure offline summaries for the browser-only Crocodyl report. No network or persistence. */
(function (root) {
  'use strict';

  function rows(entries, logicalName) {
    const bytes = entries[`tables/${logicalName}.json`];
    if (!bytes) return [];
    try {
      const value = JSON.parse(new TextDecoder().decode(bytes));
      return Array.isArray(value) ? value.filter(row => row && typeof row === 'object' && !Array.isArray(row)) : [];
    } catch (_) {
      return [];
    }
  }

  function finite(value) {
    return typeof value === 'number' && Number.isFinite(value);
  }

  function active(row) {
    return row && row.deletedAt == null;
  }

  function parseFeatures(value) {
    try {
      const parsed = typeof value === 'string' ? JSON.parse(value) : value;
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
      return Object.fromEntries(Object.entries(parsed).filter(([key, number]) => key && finite(number)));
    } catch (_) {
      return {};
    }
  }

  function summarize(entries) {
    const sessions = rows(entries, 'session').filter(active);
    const sessionById = new Map(sessions.filter(row => typeof row.id === 'string').map(row => [row.id, row]));
    const shots = rows(entries, 'shot').filter(row => sessionById.has(row.sessionId));
    const scoreSessions = rows(entries, 'score_session')
      // SQLite-backed booleans are exported as 0/1 JSON integers by the Android archive writer.
      .filter(row => active(row) && row.status === 'FINISHED' && (row.roundComplete === true || row.roundComplete === 1))
      .sort((a, b) => (a.completedAt || a.updatedAt || a.startedAt || 0) - (b.completedAt || b.updatedAt || b.startedAt || 0));
    const rounds = scoreSessions.map(row => {
      const setMatch = row.scoringKind === 'SET_MATCH';
      const maximum = finite(row.arrowsPerEnd) && finite(row.endCount) && row.arrowsPerEnd > 0 && row.endCount > 0
        ? row.arrowsPerEnd * row.endCount * 10
        : null;
      return {
        id: String(row.id ?? ''),
        name: typeof row.roundName === 'string' && row.roundName.trim() ? row.roundName : 'Completed round',
        at: finite(row.completedAt) ? row.completedAt : (finite(row.updatedAt) ? row.updatedAt : row.startedAt),
        total: finite(row.total) ? row.total : null,
        maximum: setMatch ? null : maximum,
        percentage: !setMatch && maximum && finite(row.total) ? (row.total / maximum) * 100 : null,
        xCount: finite(row.xCount) ? row.xCount : null,
        scoringKind: typeof row.scoringKind === 'string' ? row.scoringKind : '',
      };
    });

    const shotsBySession = new Map();
    for (const shot of shots) {
      const bucket = shotsBySession.get(shot.sessionId) || [];
      bucket.push(shot);
      shotsBySession.set(shot.sessionId, bucket);
    }
    const formSessions = sessions.map(session => {
      const sessionShots = shotsBySession.get(session.id) || [];
      const scored = sessionShots.filter(shot => finite(shot.score) && shot.score >= 0 && shot.score <= 10);
      return {
        id: String(session.id ?? ''),
        at: finite(session.startedAtEpochMs) ? session.startedAtEpochMs : null,
        distance: finite(session.distanceMeters) ? session.distanceMeters : null,
        duration: finite(session.durationS) ? session.durationS : (finite(session.durationAutoS) ? session.durationAutoS : null),
        arrows: finite(session.arrowsActual) ? session.arrowsActual : null,
        shotCount: sessionShots.length,
        scoredCount: scored.length,
        averageScore: scored.length ? scored.reduce((sum, shot) => sum + shot.score, 0) / scored.length : null,
        baselineCount: sessionShots.filter(shot => shot.isBaseline === true || shot.isBaseline === 1).length,
      };
    }).sort((a, b) => (b.at || 0) - (a.at || 0));

    const featureRows = shots.map(shot => ({
      shot,
      at: sessionById.get(shot.sessionId)?.startedAtEpochMs || 0,
      features: parseFeatures(shot.featuresJson),
      baseline: shot.isBaseline === true || shot.isBaseline === 1,
    })).filter(row => Object.keys(row.features).length > 0);
    const baselineRows = featureRows.filter(row => row.baseline);
    const observations = featureRows.filter(row => !row.baseline)
      .sort((a, b) => a.at - b.at || (a.shot.indexInSession || 0) - (b.shot.indexInSession || 0));
    const names = [...new Set(baselineRows.flatMap(row => Object.keys(row.features)))].sort();
    const latest = observations.at(-1);
    const baselineFeatures = names.map(name => {
      const values = baselineRows.map(row => row.features[name]).filter(finite);
      const mean = values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null;
      const variance = values.length > 1
        ? values.reduce((sum, value) => sum + ((value - mean) ** 2), 0) / (values.length - 1)
        : null;
      const standardDeviation = variance == null ? null : Math.sqrt(variance);
      const current = latest?.features[name];
      const delta = finite(current) && finite(mean) ? current - mean : null;
      return {
        name,
        baselineCount: values.length,
        mean,
        standardDeviation,
        latest: finite(current) ? current : null,
        delta,
        zScore: finite(delta) && finite(standardDeviation) && standardDeviation > 0
          ? delta / standardDeviation
          : null,
      };
    });

    const scoredRounds = rounds.filter(round => finite(round.percentage));
    const previous = scoredRounds.slice(-10, -5);
    const recent = scoredRounds.slice(-5);
    const averagePercent = values => values.length
      ? values.reduce((sum, round) => sum + round.percentage, 0) / values.length
      : null;

    return {
      formSessions,
      rounds: rounds.slice().reverse(),
      totals: {
        formSessions: sessions.length,
        formShots: shots.length,
        scoredShots: shots.filter(shot => finite(shot.score) && shot.score >= 0 && shot.score <= 10).length,
        baselineShots: baselineRows.length,
        completedRounds: rounds.length,
        averageShotScore: shots.filter(shot => finite(shot.score) && shot.score >= 0 && shot.score <= 10).length
          ? shots.filter(shot => finite(shot.score) && shot.score >= 0 && shot.score <= 10)
            .reduce((sum, shot) => sum + shot.score, 0) /
              shots.filter(shot => finite(shot.score) && shot.score >= 0 && shot.score <= 10).length
          : null,
      },
      baselineFeatures,
      latestBaselineAt: latest?.at ?? null,
      roundTrend: {
        previousCount: previous.length,
        recentCount: recent.length,
        previousAverage: averagePercent(previous),
        recentAverage: averagePercent(recent),
      },
    };
  }

  root.CrocodylReport = Object.freeze({ summarize });
})(globalThis);
