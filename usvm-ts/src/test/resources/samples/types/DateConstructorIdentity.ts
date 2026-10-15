// @ts-nocheck

class DateConstructorIdentity {
    direct(): boolean {
        return new Date(0) instanceof Date;
    }

    noArguments(): boolean {
        return new Date() instanceof Date;
    }

    argumentEffects(): boolean {
        const counter = { value: 0 };
        const date = new Date(counter.value = counter.value + 1);
        return counter.value === 1 && date instanceof Date;
    }

    stableGlobal(): boolean {
        return Date === Date && typeof Date === "function";
    }

    aliased(): boolean {
        const NativeDate = Date;
        const date = new NativeDate(0);
        return date instanceof Date && NativeDate === Date;
    }

    inheritedConstructor(): boolean {
        const date = new Date(0);
        return date.constructor === Date;
    }

    cloneThroughConstructor(): boolean {
        const date = new Date(0);
        if (date instanceof Date) {
            return new date.constructor(456) instanceof Date;
        }
        return false;
    }

    ownConstructor(): boolean {
        const date = new Date(0);
        date.constructor = ApplicationDate;
        return date.constructor === ApplicationDate && new date.constructor() instanceof ApplicationDate;
    }

    deletedOwnConstructor(): boolean {
        const date = new Date(0);
        date.constructor = ApplicationDate;
        delete date.constructor;
        return date.constructor === Date;
    }

    otherValues(): boolean {
        return !(null instanceof Date) && !(undefined instanceof Date) && !(42 instanceof Date)
            && !({ timestamp: 0 } instanceof Date) && !(Date instanceof Date);
    }

    mutatedPrototype(): boolean {
        Date.prototype.constructor = ApplicationDate;
        return new Date(0).constructor === Date;
    }

    nativeConstructor(): typeof Date {
        return Date;
    }

    shadowed(): boolean {
        const NativeDate = this.nativeConstructor();
        const Date = ApplicationDate;
        const date = new Date();
        return date instanceof Date && !(date instanceof NativeDate);
    }
}

class ApplicationDate {}
