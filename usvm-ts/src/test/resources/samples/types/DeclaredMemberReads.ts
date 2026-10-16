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

class SetterMember {
    stored = 0;
    set value(value: number) { this.stored = value; }
}

class WritableMethod {
    method(ignored: number): number { return 5; }
    replace(): number { this.method = () => 9; return 0; }
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
    setterWrite(): number {
        const instance = new SetterMember();
        instance.value = 9;
        return instance.stored;
    }
    capturedArgumentWrite(): number {
        const instance = new WritableMethod();
        return instance.method(instance.replace());
    }
    callableData(): number { return new CallableData().callback(10); }
}
