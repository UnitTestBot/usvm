// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

import { Array } from "./exported";

export class ImportedArrayShadow {
    callImportedArray(): number {
        return Array(2)[0];
    }
}
