import { MEALS, mealLabel, num } from '../fmt';
import type { DailySummary } from '../types';

interface Props {
  summary: DailySummary | null;
  loading: boolean;
  date: string;
  onDateChange: (date: string) => void;
}

/** Share of energy from each macro using 4/4/9 kcal per gram. */
export function energyShares(protein: number, carb: number, fat: number) {
  const p = protein * 4;
  const c = carb * 4;
  const f = fat * 9;
  const total = p + c + f;
  if (total === 0) return { protein: 0, carb: 0, fat: 0 };
  return { protein: (p / total) * 100, carb: (c / total) * 100, fat: (f / total) * 100 };
}

export function SummaryPanel({ summary, loading, date, onDateChange }: Props) {
  const totals = summary?.totals;
  const shares = totals ? energyShares(totals.proteinG, totals.carbG, totals.fatG) : null;
  const meals = MEALS.filter((m) => summary?.byMeal[m]);
  const maxMeal = Math.max(1, ...meals.map((m) => summary?.byMeal[m]?.kcal ?? 0));

  return (
    <aside className="card summary" aria-busy={loading}>
      <div className="summary-head">
        <h2>Daily summary</h2>
        <input
          type="date"
          value={date}
          aria-label="Date"
          onChange={(e) => e.target.value && onDateChange(e.target.value)}
        />
      </div>

      <div className="energy">
        <span className="energy-value">{totals ? num(totals.kcal) : '—'}</span>
        <span className="energy-unit">kcal</span>
      </div>

      {shares && totals && totals.kcal > 0 ? (
        <>
          <div className="stack" role="img" aria-label={`Energy from carbs ${Math.round(shares.carb)}%, protein ${Math.round(shares.protein)}%, fat ${Math.round(shares.fat)}%`}>
            <span className="seg seg-carb" style={{ width: `${shares.carb}%` }} />
            <span className="seg seg-protein" style={{ width: `${shares.protein}%` }} />
            <span className="seg seg-fat" style={{ width: `${shares.fat}%` }} />
          </div>
          <dl className="macros">
            <div>
              <dt><i className="dot seg-carb" />Carbs</dt>
              <dd>{num(totals.carbG)} g<small>{Math.round(shares.carb)}%</small></dd>
            </div>
            <div>
              <dt><i className="dot seg-protein" />Protein</dt>
              <dd>{num(totals.proteinG)} g<small>{Math.round(shares.protein)}%</small></dd>
            </div>
            <div>
              <dt><i className="dot seg-fat" />Fat</dt>
              <dd>{num(totals.fatG)} g<small>{Math.round(shares.fat)}%</small></dd>
            </div>
          </dl>
          <dl className="minor">
            <div><dt>Fibre</dt><dd>{num(totals.fiberG)} g</dd></div>
            <div><dt>Sugar</dt><dd>{num(totals.sugarG)} g</dd></div>
            <div><dt>Salt</dt><dd>{num(totals.saltG)} g</dd></div>
          </dl>
        </>
      ) : (
        <p className="hint empty">{loading ? 'Loading…' : 'Nothing counted yet for this day.'}</p>
      )}

      {meals.length > 0 && (
        <div className="meals">
          <h3>By meal</h3>
          {meals.map((meal) => {
            const kcal = summary?.byMeal[meal]?.kcal ?? 0;
            return (
              <div className="meal-row" key={meal}>
                <span>{mealLabel(meal)}</span>
                <span className="meal-bar"><i style={{ width: `${(kcal / maxMeal) * 100}%` }} /></span>
                <span className="meal-kcal">{num(kcal)}</span>
              </div>
            );
          })}
        </div>
      )}

      {summary && summary.unresolvedCount > 0 && (
        <p className="notice" role="status">
          {summary.unresolvedCount} item{summary.unresolvedCount === 1 ? '' : 's'} not counted until confirmed.
        </p>
      )}
    </aside>
  );
}
