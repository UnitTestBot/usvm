const nativeMath = Math;

export class UnmodeledGlobalBinding {
    read(): any {
        return nativeMath;
    }
}
