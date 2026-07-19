export class DualDispatch {
  value(delta: number): number {
    return 100 + delta;
  }

  static value(delta: number): number {
    return 200 + delta;
  }

  static Number(delta: number): number {
    return 300 + delta;
  }
}

export function invokeDualInstance(receiver: DualDispatch, delta: number): number {
  return receiver.value(delta);
}

export function invokeDualStatic(delta: number): number {
  return DualDispatch.value(delta);
}

export function invokeDualStaticWithShadow(value: number): number {
  return DualDispatch.value(value);
}

export function invokeStaticNamedLikeConversion(value: number): number {
  return DualDispatch.Number(value);
}
