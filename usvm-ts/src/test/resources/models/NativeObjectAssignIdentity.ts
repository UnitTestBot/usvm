const extractedNativeAssign = Object.assign;

class SourceAssignReceiver {
    assign(left: number, right: number): number {
        return left + right;
    }
}

export class NativeObjectAssignIdentity {
    static stableIdentity(): number {
        const same = Object.assign;
        const native: any = Object;
        return typeof extractedNativeAssign === "function" && same === extractedNativeAssign &&
            native.assign === extractedNativeAssign ? 1 : 0;
    }

    static extractedCall(): number {
        const assign = Object.assign;
        assign({}, { value: 1 });
        return 2;
    }

    static capturedCall(): number {
        extractedNativeAssign({}, { value: 1 });
        return 3;
    }

    static boxedCall(): number {
        const values: any[] = [Object.assign];
        const assign = values[0];
        try {
            assign(undefined, { value: 1 });
            return 0;
        } catch {
            return 8;
        }
    }

    static nullishTarget(): number {
        try {
            extractedNativeAssign(undefined, { value: 1 });
            return 0;
        } catch {
            return 4;
        }
    }

    static localShadow(): number {
        const Object = new SourceAssignReceiver();
        return Object.assign(2, 3);
    }

    static writeNative(): number {
        const native: any = Object;
        native.assign = (left: number, right: number) => left + right;
        return native.assign(2, 3);
    }

    static originalAfterReplacement(): number {
        const original = extractedNativeAssign;
        const native: any = Object;
        native.assign = () => 99;
        try {
            original(null, { value: 1 });
            return 0;
        } catch {
            return 7;
        }
    }

    static originalAfterDeletion(): number {
        const original = extractedNativeAssign;
        const native: any = Object;
        delete native.assign;
        try {
            original(null, { value: 1 });
            return 0;
        } catch {
            return 9;
        }
    }

    static deleteNative(): number {
        const native: any = Object;
        delete native.assign;
        return typeof native.assign === "undefined" ? 6 : 0;
    }
}
