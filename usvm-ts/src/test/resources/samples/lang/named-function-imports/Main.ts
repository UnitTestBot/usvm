import { named as first, replace, firstArrow } from "./First";
import { named as second, secondArrow } from "./Second";
import * as namespace from "./First";

class NamedFunctionImports {
    anonymousOwners(): number {
        return firstArrow(10) * 100 + secondArrow(20);
    }

    distinctOwners(): number {
        const alias = first;
        return alias() * 10 + second();
    }

    namespaceLiveBinding(): number {
        const alias = namespace.named;
        replace();
        const current = namespace.named;
        return alias() * 10 + current();
    }

    namespaceDirectCall(): boolean {
        return namespace.receiverIsUndefined();
    }

    namespaceOptionalCall(): boolean {
        return namespace.receiverIsUndefined?.();
    }
}
