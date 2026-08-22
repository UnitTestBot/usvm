// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

declare class ExternalReceiver {
    external(): void;
}

declare class ExternalStatic {
    static external(): void;
}

class KnownReceiver {
    known(): number {
        return 1;
    }

    toString(): string {
        throw "KnownReceiver.toString must be approximated";
    }

    valueOf(): KnownReceiver {
        throw "KnownReceiver.valueOf must be approximated";
    }
}

class EmptyReceiver {
}

class Log {
    static record(): number {
        return 999;
    }
}

class LoggerFacade {
    info(): void {
        throw "Logger calls must be approximated";
    }
}

function $r(resource: string): any {
    throw "resource lookup must be approximated";
}

class CallFallbackBaseline {
    declaredMethodWithoutBodyContinues(): number {
        const receiver = new ExternalReceiver();
        receiver.external();
        return 101;
    }

    anyReceiverWithKnownMethodContinues(receiver: any): number {
        receiver.known();
        return 102;
    }

    allocatedReceiverWithoutMethodContinues(): number {
        const receiver = new EmptyReceiver();
        receiver.missing();
        return 103;
    }

    unresolvedStaticCallPrunes(): number {
        ExternalStatic.external();
        return 104;
    }

    unresolvedVirtualCallPrunes(receiver: ExternalReceiver): number {
        receiver.external();
        return 105;
    }

    unresolvedAllocatedReceiverCallPrunes(): number {
        const receiver = new ExternalReceiver();
        receiver.external();
        return 119;
    }

    nonReferenceInstanceCallPrunes(receiver: number): number {
        receiver.missing();
        return 120;
    }

    unresolvedConstructorContinues(): number {
        new ExternalReceiver();
        return 106;
    }

    unresolvedAnyPointerCallPrunes(callback: any): number {
        callback(42);
        return 107;
    }

    nonReferencePointerCallContinues(callback: number): number {
        callback(42);
        return 108;
    }

    intraproceduralAssignmentCallContinues(): number {
        const value = this.known();
        return value + 108;
    }

    intraproceduralCallStatementContinues(): number {
        this.known();
        return 110;
    }

    logCallSkipsBody(): number {
        Log.record();
        return 111;
    }

    loggerCallSkipsBody(): number {
        const Logger = new LoggerFacade();
        Logger.info();
        return 112;
    }

    toStringUsesPlaceholder(): number {
        const receiver = new KnownReceiver();
        if (receiver.toString() === "I am a string") {
            return 113;
        }
        throw "unexpected toString approximation";
    }

    valueOfReturnsReceiver(): number {
        const receiver = new KnownReceiver();
        if (receiver.valueOf() === receiver) {
            return 114;
        }
        throw "unexpected valueOf approximation";
    }

    booleanConverterPrunes(): number {
        if (Boolean(1)) {
            return 115;
        }
        throw "unexpected Boolean approximation";
    }

    booleanConstructorUsesTruthiness(): number {
        if (new Boolean(0)) {
            throw "unexpected Boolean constructor approximation";
        }
        return 118;
    }

    mathFloorRoundsTowardNegativeInfinity(): number {
        if (Math.floor(-1.75) === -2) {
            return 116;
        }
        throw "unexpected Math.floor approximation";
    }

    resourceLookupSkipsBody(): number {
        $r("app.string.name");
        return 117;
    }

    known(): number {
        return 1;
    }
}
