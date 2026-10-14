export interface DeclarationMergedEntry {
  additional?: string;
}

export class DeclarationMergedEntry {
  classify(value: number): number {
    if (value > 10) return 1;
    return 2;
  }
}
