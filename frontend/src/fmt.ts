import type { MatchStatus, MealType } from './types';

export const MEALS: MealType[] = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK', 'UNKNOWN'];

const MEAL_LABELS: Record<MealType, string> = {
  BREAKFAST: 'Breakfast',
  LUNCH: 'Lunch',
  DINNER: 'Dinner',
  SNACK: 'Snack',
  UNKNOWN: 'Other',
};

export const mealLabel = (meal: MealType): string => MEAL_LABELS[meal];

export const STATUS_LABELS: Record<MatchStatus, string> = {
  MATCHED: 'Matched',
  CONFIRMED: 'Confirmed',
  NEEDS_CONFIRMATION: 'Needs confirmation',
  UNMATCHED: 'Not found',
};

/** One decimal at most, no trailing ".0". */
export function num(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1).replace(/\.0$/, '');
}

const UNIT_LABELS: Record<string, [string, string]> = {
  piece: ['', ''],
  g: ['g', 'g'],
  kg: ['kg', 'kg'],
  ml: ['ml', 'ml'],
  l: ['l', 'l'],
  oz: ['oz', 'oz'],
  cup: ['cup', 'cups'],
  glass: ['glass', 'glasses'],
  tbsp: ['tbsp', 'tbsp'],
  tsp: ['tsp', 'tsp'],
  slice: ['slice', 'slices'],
  bowl: ['bowl', 'bowls'],
  plate: ['plate', 'plates'],
  serving: ['serving', 'servings'],
  handful: ['handful', 'handfuls'],
  can: ['can', 'cans'],
};

/** "2", "200 g", "1 bowl", "2 bowls" */
export function amount(quantity: number, unit: string): string {
  const [singular, plural] = UNIT_LABELS[unit] ?? [unit, unit];
  const label = quantity === 1 ? singular : plural;
  return label ? `${num(quantity)} ${label}` : num(quantity);
}

export function timeOf(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
