import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { LogCard } from './components/LogCard';
import { LogForm } from './components/LogForm';
import { SummaryPanel } from './components/SummaryPanel';
import type { DailySummary, FoodLog } from './types';

const USER_KEY = 'foodlog.userId';

function loadUserId(): string {
  try {
    return localStorage.getItem(USER_KEY) || 'demo';
  } catch {
    return 'demo';
  }
}

function localToday(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

const messageOf = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong');

export default function App() {
  const [userId, setUserId] = useState(loadUserId);
  const [userDraft, setUserDraft] = useState(userId);
  /** null means "today according to the server", which avoids disagreeing with its time zone. */
  const [date, setDate] = useState<string | null>(null);
  const [logs, setLogs] = useState<FoodLog[]>([]);
  const [summary, setSummary] = useState<DailySummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const day = date ?? undefined;
    const [list, sum] = await Promise.all([api.listLogs(userId, day), api.summary(userId, day)]);
    return { list, sum };
  }, [userId, date]);

  useEffect(() => {
    let stale = false;
    setLoading(true);
    reload()
      .then(({ list, sum }) => {
        if (stale) return;
        setLogs(list);
        setSummary(sum);
        setError(null);
      })
      .catch((e) => !stale && setError(messageOf(e)))
      .finally(() => !stale && setLoading(false));
    return () => {
      stale = true;
    };
  }, [reload]);

  /** Runs a write, then refreshes the view (jumping back to today so the new entry is visible). */
  const mutate = async (action: () => Promise<unknown>): Promise<boolean> => {
    setBusy(true);
    setError(null);
    try {
      await action();
      if (date !== null) {
        setDate(null);
      } else {
        const { list, sum } = await reload();
        setLogs(list);
        setSummary(sum);
      }
      return true;
    } catch (e) {
      setError(messageOf(e));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const commitUser = () => {
    const next = userDraft.trim();
    if (!next) {
      setUserDraft(userId);
      return;
    }
    if (next !== userId) {
      try {
        localStorage.setItem(USER_KEY, next);
      } catch {
        /* storage unavailable, keep going without persistence */
      }
      setUserId(next);
      setDate(null);
    }
  };

  const displayDate = date ?? summary?.date ?? localToday();
  const ordered = [...logs].reverse();

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">🥗</span>
          <div>
            <h1>Food Log</h1>
            <p>Say what you ate. Get the nutrition.</p>
          </div>
        </div>
        <label className="user">
          <span>User</span>
          <input
            value={userDraft}
            maxLength={100}
            onChange={(e) => setUserDraft(e.target.value)}
            onBlur={commitUser}
            onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
          />
        </label>
      </header>

      {error && (
        <div className="banner" role="alert">
          <span>{error}</span>
          <button type="button" className="link" onClick={() => setError(null)}>Dismiss</button>
        </div>
      )}

      <main className="layout">
        <div className="area-composer">
          <LogForm
            busy={busy}
            onText={(text) => mutate(() => api.logText(userId, text))}
            onAudio={async (audio, filename) => {
              await mutate(() => api.logVoice(userId, audio, filename));
            }}
          />
        </div>

        <div className="area-summary">
          <SummaryPanel summary={summary} loading={loading} date={displayDate} onDateChange={setDate} />
        </div>

        <section className="area-logs">
          <h2 className="section-title">
            {date === null || date === localToday() ? 'Today' : displayDate}
            <span className="count">{logs.length}</span>
          </h2>

          {loading && logs.length === 0 && <p className="hint">Loading…</p>}
          {!loading && logs.length === 0 && (
            <div className="card empty">
              <p>No meals logged for this day yet.</p>
              <p className="hint">Try “I had a banana and a glass of milk” above.</p>
            </div>
          )}
          {ordered.map((log) => (
            <LogCard
              key={log.id}
              log={log}
              busy={busy}
              onConfirm={(logId, index, foodId) => void mutate(() => api.confirmItem(logId, index, foodId))}
            />
          ))}
        </section>
      </main>
    </div>
  );
}
