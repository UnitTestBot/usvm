export function bothConditions(value: number): boolean {
  if (value > 0 && value < 10) {
    return true;
  } else {
    return false;
  }
}

export function eitherCondition(value: number): boolean {
  if (value < 0 || value > 10) {
    return true;
  } else {
    return false;
  }
}
