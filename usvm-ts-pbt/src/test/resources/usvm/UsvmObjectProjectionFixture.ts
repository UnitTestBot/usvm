class AnnotatedValue {
    value: number = 0;
    ignored: () => number = () => 10;
}

class ParentValue {
    value: number = 0;
}

class ChildValue extends ParentValue {
    value: number = 0;
}

export function objectMutation(value: { count: number }): boolean {
    const before = value.count;
    value.count += 1;
    return before !== 2;
}

export function plainClassIdentity(value: AnnotatedValue): boolean {
    return !(value instanceof AnnotatedValue);
}

export function absentClassField(value: AnnotatedValue): boolean {
    return value.ignored === undefined;
}

export function inheritedField(value: ChildValue): boolean {
    return value.value !== 2;
}

export function nestedObjects(value: { entries: { count: number }[] }): boolean {
    const before = value.entries[0].count;
    value.entries[0].count = 9;
    return before !== 2;
}

export function optionalObject(value: { count: number } | undefined): boolean {
    if (value === undefined) return true;
    const before = value.count;
    value.count = 9;
    return before !== 2;
}

export function optionalField(value: { count?: number }): boolean {
    if (value.count === undefined) return true;
    return value.count !== 2;
}

export function untypedObject(value: any): boolean {
    return value.count !== 2;
}

export function positiveObject(value: { count: number }): boolean {
    return value.count > 0;
}

export function acceptsBooleanField(value: { count: boolean }): boolean {
    return value.count;
}

export function optionalCollection(value: { entries: number[] } | undefined): boolean {
    return value === undefined || value.entries.length >= 0;
}
