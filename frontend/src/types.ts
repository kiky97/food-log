export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK' | 'UNKNOWN';
export type MatchStatus = 'MATCHED' | 'NEEDS_CONFIRMATION' | 'UNMATCHED' | 'CONFIRMED';

export interface Nutrients {
  kcal: number;
  proteinG: number;
  fatG: number;
  carbG: number;
  fiberG: number;
  sugarG: number;
  saltG: number;
}

export interface Candidate {
  foodId: string;
  name: string;
  score: number;
}

export interface LogItem {
  index: number;
  rawFood: string;
  quantity: number;
  unit: string;
  mealType: MealType;
  status: MatchStatus;
  foodId: string | null;
  foodName: string | null;
  confidence: number;
  grams: number | null;
  portionEstimated: boolean;
  nutrients: Nutrients;
  candidates: Candidate[];
}

export interface FoodLog {
  id: string;
  userId: string;
  loggedAt: string;
  logDate: string;
  source: 'TEXT' | 'VOICE';
  transcript: string;
  items: LogItem[];
  totals: Nutrients;
  needsAttention: number;
}

export interface DailySummary {
  userId: string;
  date: string;
  totals: Nutrients;
  byMeal: Partial<Record<MealType, Nutrients>>;
  itemCount: number;
  unresolvedCount: number;
}

export interface SearchHit {
  foodId: string;
  name: string;
  category: string;
  score: number;
}
