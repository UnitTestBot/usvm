const nativeJson = JSON;

export class UnmodeledGlobalBinding {
    read(): any {
        return nativeJson;
    }
}
