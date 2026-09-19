import { num, timeOf } from '../fmt';
import type { FoodLog } from '../types';
import { ItemRow } from './ItemRow';

interface Props {
  log: FoodLog;
  busy?: boolean;
  onConfirm: (logId: string, index: number, foodId: string) => void;
}

export function LogCard({ log, busy, onConfirm }: Props) {
  return (
    <article className="card log">
      <header className="log-head">
        <span className="log-time">{timeOf(log.loggedAt)}</span>
        <span className="tag">{log.source === 'VOICE' ? 'Voice' : 'Text'}</span>
        {log.needsAttention > 0 && (
          <span className="badge badge-needs_confirmation">{log.needsAttention} to review</span>
        )}
      </header>
      <blockquote className="transcript">{log.transcript}</blockquote>
      <ul className="items">
        {log.items.map((item) => (
          <ItemRow key={item.index} item={item} busy={busy} onConfirm={(index, foodId) => onConfirm(log.id, index, foodId)} />
        ))}
      </ul>
      <footer className="log-foot">
        <span>Total</span>
        <strong>{num(log.totals.kcal)} kcal</strong>
      </footer>
    </article>
  );
}
