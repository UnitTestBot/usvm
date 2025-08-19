class A { kindId: 0; a: number };
class B { kindId: 1; b: string };

class Example {
  // Requires property "x" to exist and be a number (success → 1, otherwise 2).
  f1(o: any) {
    if ("x" in o && typeof o.x === "number") return 1;
    return 2;
  }

  // Succeeds when property "x" is missing (return 1), otherwise 2.
  f2(o: any) {
    if (!("x" in o)) return 1;
    return 2;
  }

  // Explicit numeric guard on o.x, then checks o.x + 1 > 0 (success → 1, otherwise 2).
  f3a(o: any) {
    if (typeof o.x === "number" && o.x + 1 > 0) return 1;
    return 2;
  }

  // f3b: coercion showcase — success iff (o.x + 1) is NaN (e.g., undefined + 1 → NaN)
  f3b(o: any) {
    const y = o.x + 1;
    if (Number.isNaN(y)) return 1;
    return 2;
  }

  // Writes o.x, deletes it, then requires that "x" is absent (unreachable branch if still present).
  f4(o: any) {
    o.x = 1;
    delete o.x;
    if (o.x !== undefined) return -1; // unreachable
    return 1;
  }

  // (1) f5 — correlated integers under coercion
  f5(o: any) {
    const ai = (Number(o.a) | 0);
    const bi = (Number(o.b) | 0);
    const s = ai + bi;
    if (s === 0 && o.a !== o.b) return 1;
    return 2;
  }

  // Strict equality: only numeric 0 satisfies (success → 1, otherwise 2).
  f6_strict(o: any) {
    if (o.x === 0) return 1;
    return 2;
  }

  // Loose equality: values coercible to 0 (0, "0", false) satisfy (success → 1, otherwise 2).
  f7_loose(o: any) {
    if (o.x == 0) return 1;
    return 2;
  }

  // Nullish check via `== null`: success when o.x is null or undefined (return 1), else 2.
  f8(o: any) {
    if (o.x == null) return 1;
    return 2;
  }

  // Nested requirement: x must exist/truthy, contain y, and y must be true (success → 1, else 2).
  f9(o: any) {
    if ("x" in o && o.x && "y" in o.x && o.x.y === true) return 1;
    return 2;
  }

  // Optional chaining: success when x.y exists and equals 1 (otherwise 2).
  f10_optchain(o: any) {
    if (o.x?.y === 1) return 1;
    return 2;
  }

  // NaN check: success when x is a number and x !== x (i.e., NaN), otherwise 2.
  f11_nan(o: any) {
    if (typeof o.x === "number" && o.x !== o.x) return 1;
    return 2;
  }
}
