// @ts-nocheck

class ObjectConstraintBase {
    marker?: number;
}

class ObjectConstraintChild extends ObjectConstraintBase {}
class ObjectConstraintOther { marker?: number; }

class RuntimeObjectConstraints {
    propertyBeforeInstance(value: { marker?: number }): number {
        const present = "marker" in value;
        if (value instanceof ObjectConstraintBase) return present ? 1 : 2;
        return present ? 3 : 4;
    }

    instanceBeforeProperty(value: { marker?: number }): number {
        const matches = value instanceof ObjectConstraintBase;
        const present = "marker" in value;
        if (matches) return present ? 1 : 2;
        return present ? 3 : 4;
    }

    nonObjectControls(): number {
        if (null instanceof ObjectConstraintBase) return -1;
        if (undefined instanceof ObjectConstraintBase) return -2;
        if (42 instanceof ObjectConstraintBase) return -3;
        if ("value" instanceof ObjectConstraintBase) return -4;
        if (false instanceof ObjectConstraintBase) return -5;
        return 0;
    }
}
