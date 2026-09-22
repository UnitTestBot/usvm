export const usesCapturedBuiltins = (value: number): boolean => {
  if (!Number.isInteger(value)) {
    throw new Error('expected an integer')
  }

  return value % 2 === 0
}

export const capturedBuiltinsWithTwoInputs = (left: number, right: number): boolean =>
  Number.isFinite(left) && left === right

let capturesModuleValue: (value: number) => boolean

{
  const threshold = 3
  capturesModuleValue = (value: number): boolean => value > threshold
}

export { capturesModuleValue }
