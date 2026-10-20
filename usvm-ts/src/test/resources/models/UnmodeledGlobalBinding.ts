const nativeReflect = Reflect;

export class UnmodeledGlobalBinding {
    read(): any {
        return nativeReflect;
    }
}
