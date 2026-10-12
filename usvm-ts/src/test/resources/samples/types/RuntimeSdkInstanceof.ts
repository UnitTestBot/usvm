import { RuntimeSdkParent, RuntimeSdkChild } from "./RuntimeSdkClasses";

class RuntimeSdkInstanceof {
    constructorObject(): number { return RuntimeSdkChild instanceof Object ? 1 : 0; }
    constructorFunction(): number { return RuntimeSdkChild instanceof Function ? 1 : 0; }
    constructorParent(): number { return RuntimeSdkChild instanceof RuntimeSdkParent ? 1 : 0; }
    inherited(value: RuntimeSdkChild): number { return value instanceof RuntimeSdkParent ? 1 : 0; }
}
