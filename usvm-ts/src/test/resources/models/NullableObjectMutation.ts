// @ts-nocheck

class UserObjectMutation {
    assign(target: any): number {
        return target === null ? 19 : 23;
    }
}

export class NullableObjectMutation {
    static assignNull(): number {
        try {
            Object.assign(null, { value: 1 });
            return 0;
        } catch (error) {
            return 1;
        }
    }

    static assignUndefined(): number {
        try {
            Object.assign(undefined, { value: 1 });
            return 0;
        } catch (error) {
            return 1;
        }
    }

    static uncaughtAssignUndefined(): number {
        Object.assign(undefined, { value: 1 });
        return 0;
    }

    static nullableChoice(flag: boolean): number {
        const target: any = flag ? undefined : {};
        try {
            Object.assign(target, {});
            return flag ? -1 : 2;
        } catch (error) {
            return flag ? 1 : -1;
        }
    }

    static arrayChoice(flag: boolean): number {
        const target: any = flag ? [] : {};
        Object.assign(target, {});
        return flag ? -1 : 7;
    }

    static freezeNullish(): number {
        Object.freeze(null);
        Object.seal(undefined);
        Object.preventExtensions(null);
        return 11;
    }

    static userReceiver(): number {
        return new UserObjectMutation().assign(null);
    }

    static undefinedPrototype(): number {
        const value: any = undefined;
        try {
            return value.__proto__;
        } catch (error) {
            return 13;
        }
    }
}
