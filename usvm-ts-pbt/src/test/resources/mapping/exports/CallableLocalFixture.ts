export const arrowPredicate = (value: number): boolean => value > 0;

const functionPredicate = function (value: number): boolean {
  return value !== 0;
};
export { functionPredicate as aliasedPredicate };

export const nonCallable = 42;

export let reassignedPredicate = (value: number): boolean => value > 0;
reassignedPredicate = (value: number): boolean => value < 0;
