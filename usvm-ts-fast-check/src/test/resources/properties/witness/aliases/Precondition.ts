import { admitted } from '@witness/logic';

export function admission(value: number): boolean {
  return admitted(value);
}
