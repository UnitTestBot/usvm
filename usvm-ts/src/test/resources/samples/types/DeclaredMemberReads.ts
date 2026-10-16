class PrototypeMembers {
    get value(): number { return 3; }
    method(): number { return 5; }
}

class InheritedMembers extends PrototypeMembers {}

class OwnData extends PrototypeMembers {
    method: any = 7;
}

class CallableData {
    value = 1;
    callback = (value: number): number => value + 1;
}

export class DeclaredMemberReads {
    getter(): number { return new PrototypeMembers().value; }
    inheritedGetter(): number { return new InheritedMembers().value; }
    methodValue(): string { return typeof new PrototypeMembers().method; }
    inheritedMethodValue(): string { return typeof new InheritedMembers().method; }
    directCall(): number { return new PrototypeMembers().method(); }
    inheritedCall(): number { return new InheritedMembers().method(); }
    ownData(): number { return new OwnData().method; }
    ownWrite(): number {
        const instance = new CallableData();
        instance.value = 9;
        return instance.value;
    }
    methodWrite(): number {
        const instance: any = new PrototypeMembers();
        instance.method = 9;
        return instance.method;
    }
    getterWrite(): number {
        const instance: any = new PrototypeMembers();
        instance.value = 9;
        return instance.value;
    }
    callableData(): number { return new CallableData().callback(10); }
}
