import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { ItemRow } from '../components/ItemRow';
import type { LogItem } from '../types';

vi.mock('../api', () => ({ api: { searchFoods: vi.fn().mockResolvedValue([]) } }));

const zero = { kcal: 0, proteinG: 0, fatG: 0, carbG: 0, fiberG: 0, sugarG: 0, saltG: 0 };

const base: LogItem = {
  index: 2,
  rawFood: 'chicken soup',
  quantity: 1,
  unit: 'bowl',
  mealType: 'LUNCH',
  status: 'NEEDS_CONFIRMATION',
  foodId: null,
  foodName: null,
  confidence: 0.65,
  grams: null,
  portionEstimated: false,
  nutrients: zero,
  candidates: [{ foodId: 'S024', name: 'Chicken breast cooked', score: 0.65 }],
};

afterEach(cleanup);

describe('ItemRow', () => {
  it('offers candidates for an item that needs confirmation and reports the pick', () => {
    const onConfirm = vi.fn();
    render(<ul><ItemRow item={base} onConfirm={onConfirm} /></ul>);

    expect(screen.getByText('Needs confirmation')).toBeTruthy();
    expect(screen.getByText('Which food did you mean?')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Chicken breast cooked/ }));
    expect(onConfirm).toHaveBeenCalledWith(2, 'S024');
  });

  it('shows nutrition for a matched item and lets the user correct it', () => {
    const matched: LogItem = {
      ...base, rawFood: 'eggs', quantity: 2, unit: 'piece', status: 'MATCHED', foodId: 'S018',
      foodName: 'Egg boiled', grams: 100, nutrients: { ...zero, kcal: 155 }, candidates: [],
    };
    const onConfirm = vi.fn();
    render(<ul><ItemRow item={matched} onConfirm={onConfirm} /></ul>);

    expect(screen.getByText('Egg boiled')).toBeTruthy();
    expect(screen.getByText('155')).toBeTruthy();
    expect(screen.queryByText('Which food did you mean?')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Wrong food?' }));
    expect(screen.getByText('Pick the correct food:')).toBeTruthy();
  });

  it('marks estimated portions', () => {
    const estimated: LogItem = {
      ...base, status: 'MATCHED', foodId: 'S024', foodName: 'Chicken breast cooked', grams: 100,
      portionEstimated: true, nutrients: { ...zero, kcal: 165 }, candidates: [],
    };
    render(<ul><ItemRow item={estimated} onConfirm={vi.fn()} /></ul>);

    expect(screen.getByText(/≈\s*100 g/)).toBeTruthy();
  });
});
