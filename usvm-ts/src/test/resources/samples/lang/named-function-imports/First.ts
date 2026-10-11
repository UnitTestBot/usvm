export function named(): number { return 1; }
export function replace(): void { named = replacement; }
function replacement(): number { return 3; }
export function receiverIsUndefined(): boolean { return this === undefined; }
