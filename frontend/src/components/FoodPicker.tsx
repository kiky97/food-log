import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Candidate, SearchHit } from '../types';

interface Props {
  candidates: Candidate[];
  disabled?: boolean;
  onPick: (foodId: string) => void;
}

/** Suggested foods plus a search box, used to resolve or correct a match. */
export function FoodPicker({ candidates, disabled, onPick }: Props) {
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<SearchHit[]>([]);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setHits([]);
      return;
    }
    let stale = false;
    setSearching(true);
    const timer = window.setTimeout(() => {
      api
        .searchFoods(q)
        .then((result) => !stale && setHits(result))
        .catch(() => !stale && setHits([]))
        .finally(() => !stale && setSearching(false));
    }, 300);
    return () => {
      stale = true;
      window.clearTimeout(timer);
    };
  }, [query]);

  /** Search hits already offered as a suggestion chip are dropped so nothing appears twice. */
  const visibleHits = hits.filter((h) => !candidates.some((c) => c.foodId === h.foodId));

  return (
    <div className="picker">
      {candidates.length > 0 && (
        <div className="chips" aria-label="Suggested foods">
          {candidates.map((c) => (
            <button key={c.foodId} type="button" className="chip" disabled={disabled} onClick={() => onPick(c.foodId)}>
              {c.name}
              <span className="chip-score">{Math.round(c.score * 100)}%</span>
            </button>
          ))}
        </div>
      )}
      <input
        type="search"
        className="picker-search"
        placeholder="Search the food database…"
        aria-label="Search foods"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        disabled={disabled}
      />
      {searching && <p className="hint">Searching…</p>}
      {!searching && query.trim().length >= 2 && visibleHits.length === 0 && <p className="hint">No foods found.</p>}
      {visibleHits.length > 0 && (
        <ul className="hits">
          {visibleHits.map((h) => (
            <li key={h.foodId}>
              <button type="button" disabled={disabled} onClick={() => onPick(h.foodId)}>
                <span>{h.name}</span>
                <span className="hint">{h.category}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {candidates.length === 0 && visibleHits.length === 0 && query.trim().length < 2 && (
        <p className="hint">Type at least two letters to search.</p>
      )}
    </div>
  );
}
