import type { Fields, Values } from './types';
export const emptyFields = (): Fields => ({ chefId: null, chefSearch: '', video: null, videoUrl: '', mentionType: '', timestamp: '', note: '' });
export function timestampSeconds(input: string): number | null {
  if (!input.trim()) return null;
  const parts = input.trim().split(':');
  if ((parts.length !== 2 && parts.length !== 3) || parts.some(p => !/^\d+$/.test(p))) throw new Error('시점은 분:초 또는 시:분:초로 입력하세요.');
  const numbers = parts.map(Number);
  if (numbers.at(-1)! > 59 || (numbers.length === 3 && numbers[1] > 59)) throw new Error('초와 중간 분은 0~59로 입력하세요.');
  const seconds = numbers.reduce((sum, n) => sum * 60 + n, 0);
  if (!Number.isSafeInteger(seconds) || seconds > 2147483647) throw new Error('시점이 허용 범위를 초과했습니다.');
  return seconds;
}
export function timestampText(seconds: number | null): string {
  return seconds === null ? '' : `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}
export function values(fields: Fields): Values {
  if (fields.note.length > 2000) throw new Error('검증 메모는 최대 2000자입니다.');
  return { chefId: fields.chefId, videoId: fields.video?.id ?? null, mentionType: fields.mentionType || null,
    timestampSeconds: timestampSeconds(fields.timestamp), verificationNote: fields.note.trim() || null };
}
export function changedValues(next: Values, baseline: Values): Partial<Values> {
  return Object.fromEntries((Object.keys(next) as (keyof Values)[]).filter(key => next[key] !== baseline[key]).map(key => [key, next[key]]));
}
