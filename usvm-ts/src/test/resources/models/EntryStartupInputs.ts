type Reader = (value: number) => number;
type Big = bigint;

export class EntryStartupInputs {
  independent(): number {
    return 17;
  }

  supported(value: number): number {
    if (value > 10) return 1;
    return 2;
  }

  withCallback(reader: Reader): number {
    return reader(2);
  }

  withBigInt(value: Big): Big {
    return value;
  }

  withGenericBigInt<T extends bigint>(value: T): T {
    return value;
  }
}
