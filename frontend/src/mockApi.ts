import type { Api } from './api';
import type { DailySummary, FoodLog, LogItem, MealType, Nutrients, SearchHit } from './types';

/**
 * A tiny in-memory stand-in for the backend, for UI development and demos. It recognises a handful
 * of foods with a naive keyword parser; it is not a model of the real matching or extraction logic.
 */

interface MockFood {
  id: string;
  name: string;
  keywords: string[];
  per100g: Nutrients;
  pieceGrams: number;
}

const n = (kcal: number, proteinG: number, fatG: number, carbG: number, fiberG = 0, sugarG = 0, saltG = 0): Nutrients => ({
  kcal, proteinG, fatG, carbG, fiberG, sugarG, saltG,
});

const FOODS: MockFood[] = [
  { id: 'S018', name: 'Egg boiled', keywords: ['egg'], per100g: n(155, 12.6, 10.6, 1.1, 0, 1.1, 0.4), pieceGrams: 50 },
  { id: 'S012', name: 'Rice white cooked', keywords: ['rice'], per100g: n(130, 2.7, 0.3, 28.2, 0.4, 0.1), pieceGrams: 200 },
  { id: 'S001', name: 'Apple', keywords: ['apple'], per100g: n(52, 0.3, 0.2, 13.8, 2.4, 10.4), pieceGrams: 180 },
  { id: 'S002', name: 'Banana', keywords: ['banana'], per100g: n(89, 1.1, 0.3, 22.8, 2.6, 12.2), pieceGrams: 120 },
  { id: 'S024', name: 'Chicken breast cooked', keywords: ['chicken'], per100g: n(165, 31, 3.6, 0, 0, 0, 0.1), pieceGrams: 120 },
  { id: 'S027', name: 'Salmon cooked', keywords: ['salmon'], per100g: n(206, 22.1, 12.4, 0, 0, 0, 0.1), pieceGrams: 150 },
  { id: 'S015', name: 'Bread white', keywords: ['bread', 'toast'], per100g: n(265, 9, 3.2, 49, 2.7, 5, 1.2), pieceGrams: 30 },
  { id: 'S020', name: 'Milk whole', keywords: ['milk'], per100g: n(64, 3.3, 3.6, 4.7, 0, 4.7, 0.1), pieceGrams: 244 },
];

const NUMBER_WORDS: Record<string, number> = { a: 1, an: 1, one: 1, two: 2, three: 3, four: 4, five: 5, half: 0.5 };
const ZERO: Nutrients = n(0, 0, 0, 0);

const scale = (x: Nutrients, f: number): Nutrients => ({
  kcal: x.kcal * f, proteinG: x.proteinG * f, fatG: x.fatG * f, carbG: x.carbG * f,
  fiberG: x.fiberG * f, sugarG: x.sugarG * f, saltG: x.saltG * f,
});
const plus = (a: Nutrients, b: Nutrients): Nutrients => ({
  kcal: a.kcal + b.kcal, proteinG: a.proteinG + b.proteinG, fatG: a.fatG + b.fatG, carbG: a.carbG + b.carbG,
  fiberG: a.fiberG + b.fiberG, sugarG: a.sugarG + b.sugarG, saltG: a.saltG + b.saltG,
});
const round1 = (x: Nutrients): Nutrients => {
  const r = (v: number) => Math.round(v * 10) / 10;
  return { kcal: r(x.kcal), proteinG: r(x.proteinG), fatG: r(x.fatG), carbG: r(x.carbG),
    fiberG: r(x.fiberG), sugarG: r(x.sugarG), saltG: r(x.saltG) };
};

const today = () => new Date().toISOString().slice(0, 10);
const delay = <T,>(value: T): Promise<T> => new Promise((resolve) => setTimeout(() => resolve(value), 350));

let logs: FoodLog[] = [];
let nextId = 1;

function detectMeal(text: string): MealType {
  const m = /\b(breakfast|lunch|dinner|snack)\b/i.exec(text);
  return m ? (m[1].toUpperCase() as MealType) : 'UNKNOWN';
}

function resolved(item: LogItem, food: MockFood, status: LogItem['status']): LogItem {
  const grams = item.unit === 'g' ? item.quantity : item.quantity * food.pieceGrams;
  return {
    ...item, status, foodId: food.id, foodName: food.name, confidence: status === 'CONFIRMED' ? 1 : 0.97,
    grams, portionEstimated: false, nutrients: round1(scale(food.per100g, grams / 100)), candidates: [],
  };
}

function parse(text: string): LogItem[] {
  const meal = detectMeal(text);
  const cleaned = text.toLowerCase().replace(/\b(for|as)\s+(a\s+)?(breakfast|lunch|dinner|snack)\b/g, ' ');
  const items: LogItem[] = [];
  for (const segment of cleaned.split(/,|;|\band\b/)) {
    const words = segment.trim().split(/\s+/).filter(Boolean);
    if (words.length === 0) continue;
    const gramMatch = /(\d+(?:\.\d+)?)\s*g\b/.exec(segment);
    const numberWord = words.find((w) => /^\d+(\.\d+)?$/.test(w) || w in NUMBER_WORDS);
    const quantity = gramMatch
      ? Number(gramMatch[1])
      : numberWord ? (NUMBER_WORDS[numberWord] ?? Number(numberWord)) : 1;
    const food = FOODS.find((f) => f.keywords.some((k) => segment.includes(k)));
    const rawFood = words.filter((w) => !(w in NUMBER_WORDS) && !/^\d/.test(w) && !['i', 'had', 'ate', 'of', 'bowl', 'some'].includes(w)).join(' ') || segment.trim();
    const base: LogItem = {
      index: items.length, rawFood, quantity, unit: gramMatch ? 'g' : /\bbowl\b/.test(segment) ? 'bowl' : 'piece', mealType: meal, status: 'UNMATCHED',
      foodId: null, foodName: null, confidence: 0, grams: null, portionEstimated: false, nutrients: ZERO, candidates: [],
    };
    if (!food) {
      items.push(base);
    } else if (words.length > 1 && /soup|salad|pudding|sauce/.test(segment)) {
      items.push({ ...base, status: 'NEEDS_CONFIRMATION', confidence: 0.65,
        candidates: [{ foodId: food.id, name: food.name, score: 0.65 }] });
    } else {
      items.push(resolved(base, food, 'MATCHED'));
    }
  }
  return items;
}

const totalOf = (items: LogItem[]): Nutrients =>
  round1(items.filter((i) => i.status === 'MATCHED' || i.status === 'CONFIRMED').reduce((sum, i) => plus(sum, i.nutrients), ZERO));

export const mockApi: Api = {
  async logText(userId, text) {
    const items = parse(text);
    if (items.length === 0) throw Object.assign(new Error('No food or drink was found'), { status: 422 });
    const log: FoodLog = {
      id: `mock-${nextId++}`, userId, loggedAt: new Date().toISOString(), logDate: today(), source: 'TEXT',
      transcript: text, items, totals: totalOf(items),
      needsAttention: items.filter((i) => i.status === 'NEEDS_CONFIRMATION' || i.status === 'UNMATCHED').length,
    };
    logs = [...logs, log];
    return delay(log);
  },

  async logVoice(userId, audio) {
    const text = audio.type.startsWith('audio/') ? 'I had two eggs and a bowl of rice for breakfast' : await audio.text();
    return mockApi.logText(userId, text);
  },

  async confirmItem(logId, index, foodId) {
    const log = logs.find((l) => l.id === logId);
    const food = FOODS.find((f) => f.id === foodId);
    if (!log || !food) throw Object.assign(new Error('Not found'), { status: 404 });
    const items = log.items.map((i) => (i.index === index ? resolved(i, food, 'CONFIRMED') : i));
    const updated: FoodLog = { ...log, items, totals: totalOf(items),
      needsAttention: items.filter((i) => i.status === 'NEEDS_CONFIRMATION' || i.status === 'UNMATCHED').length };
    logs = logs.map((l) => (l.id === logId ? updated : l));
    return delay(updated);
  },

  listLogs: (userId, date) => delay(logs.filter((l) => l.userId === userId && l.logDate === (date ?? today()))),

  async summary(userId, date) {
    const day = date ?? today();
    const items = logs.filter((l) => l.userId === userId && l.logDate === day).flatMap((l) => l.items);
    const byMeal: DailySummary['byMeal'] = {};
    let unresolved = 0;
    for (const item of items) {
      if (item.status === 'MATCHED' || item.status === 'CONFIRMED') {
        byMeal[item.mealType] = round1(plus(byMeal[item.mealType] ?? ZERO, item.nutrients));
      } else {
        unresolved++;
      }
    }
    return delay({ userId, date: day, totals: totalOf(items), byMeal, itemCount: items.length, unresolvedCount: unresolved });
  },

  async searchFoods(query): Promise<SearchHit[]> {
    const q = query.trim().toLowerCase();
    return delay(FOODS.filter((f) => f.name.toLowerCase().includes(q) || f.keywords.some((k) => q.includes(k)))
      .map((f) => ({ foodId: f.id, name: f.name, category: 'Mock', score: 0.9 })));
  },
};
