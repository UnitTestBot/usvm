import { holds } from '@witness/logic';

export function threshold(value: number): boolean {
  return holds(value);
}
