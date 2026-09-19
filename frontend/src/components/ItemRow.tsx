import { useState } from 'react';
import { STATUS_LABELS, amount, mealLabel, num } from '../fmt';
import type { LogItem } from '../types';
import { FoodPicker } from './FoodPicker';

interface Props {
  item: LogItem;
  busy?: boolean;
  onConfirm: (index: number, foodId: string) => void;
}

export function ItemRow({ item, busy, onConfirm }: Props) {
  const [correcting, setCorrecting] = useState(false);
  const unresolved = item.status === 'NEEDS_CONFIRMATION' || item.status === 'UNMATCHED';

  const pick = (foodId: string) => {
    setCorrecting(false);
    onConfirm(item.index, foodId);
  };

  return (
    <li className={`item ${unresolved ? 'item-open' : ''}`}>
      <div className="item-main">
        <div className="item-title">
          <span className="item-amount">{amount(item.quantity, item.unit)}</span>
          <span className="item-name">{item.foodName ?? item.rawFood}</span>
          {item.foodName && item.foodName.toLowerCase() !== item.rawFood.toLowerCase() && (
            <span className="item-heard">heard “{item.rawFood}”</span>
          )}
        </div>
        <div className="item-meta">
          <span className={`badge badge-${item.status.toLowerCase()}`}>{STATUS_LABELS[item.status]}</span>
          {item.mealType !== 'UNKNOWN' && <span className="tag">{mealLabel(item.mealType)}</span>}
          {item.grams != null && (
            <span className="tag" title={item.portionEstimated ? 'Portion size is a generic estimate' : undefined}>
              {item.portionEstimated ? '≈ ' : ''}
              {num(item.grams)} g
            </span>
          )}
        </div>
      </div>

      <div className="item-side">
        {unresolved ? (
          <span className="kcal kcal-muted">—</span>
        ) : (
          <span className="kcal">
            {num(item.nutrients.kcal)}
            <small> kcal</small>
          </span>
        )}
        {!unresolved && (
          <button type="button" className="link" disabled={busy} onClick={() => setCorrecting((v) => !v)}>
            {correcting ? 'Cancel' : 'Wrong food?'}
          </button>
        )}
      </div>

      {(unresolved || correcting) && (
        <div className="item-picker">
          <p className="picker-title">
            {unresolved
              ? item.status === 'UNMATCHED'
                ? 'No close match. Search for the right food:'
                : 'Which food did you mean?'
              : 'Pick the correct food:'}
          </p>
          <FoodPicker candidates={item.candidates} disabled={busy} onPick={pick} />
        </div>
      )}
    </li>
  );
}
