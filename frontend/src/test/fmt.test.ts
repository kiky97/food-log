import { describe, expect, it } from 'vitest';
import { amount, num } from '../fmt';
import { energyShares } from '../components/SummaryPanel';

describe('num', () => {
  it('drops trailing zeros and keeps one decimal', () => {
    expect(num(508)).toBe('508');
    expect(num(508.6)).toBe('508.6');
    expect(num(93.04)).toBe('93');
    expect(num(0.25)).toBe('0.3');
  });
});

describe('amount', () => {
  it('formats counts, weights and pluralised units', () => {
    expect(amount(2, 'piece')).toBe('2');
    expect(amount(150, 'g')).toBe('150 g');
    expect(amount(1, 'bowl')).toBe('1 bowl');
    expect(amount(2, 'bowl')).toBe('2 bowls');
    expect(amount(1.5, 'glass')).toBe('1.5 glasses');
  });

  it('passes unknown units through', () => {
    expect(amount(3, 'zorb')).toBe('3 zorb');
  });
});

describe('energyShares', () => {
  it('splits energy 4/4/9 and sums to 100', () => {
    const s = energyShares(25, 50, 10); // 100 + 200 + 90 kcal
    expect(s.protein + s.carb + s.fat).toBeCloseTo(100);
    expect(s.carb).toBeGreaterThan(s.protein);
    expect(s.fat).toBeCloseTo((90 / 390) * 100);
  });

  it('handles an empty day', () => {
    expect(energyShares(0, 0, 0)).toEqual({ protein: 0, carb: 0, fat: 0 });
  });
});
