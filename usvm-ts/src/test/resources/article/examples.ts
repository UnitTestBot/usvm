
class A = { kind: "A"; a: number };
class B = { kind: "B"; b: string };

class Example {
    // f1: "x" must exist and be number
    f1(o: any) {
      if ("x" in o && typeof o.x === "number") return 1;
      return 2;
    }

    // f2: throws if "x" is missing
    f2(o: any) {
      if (!("x" in o)) return 1;
      return 2;
    }

    // f3a: require number explicitly, still use '+' in the branch
    f3a(o: any) {
      if (typeof o.x === "number" && o.x + 1 > 0) return 1;
      return -1
    }

    // f3b: '+' coerces many values to number — beware!
    f3b(o: any) {
      const y = o.x + 1;
      if (typeof y === "number") return 1;
      return -1;
    }

    // f4: writes then deletes; checks absence
    f4(o: any) {
      o.x = 1;            // создаётся *внутри*, не часть входа
      delete o.x;
      if ("x" in o) return -1; // unreachable
      return 1;
    }

    // f5: discriminated union
    f5(o: A | B) {
      if (o.kind === "A" && o.a > 0) return 1;
      return -1;
    }

    // f6: method presence only
    f6(o: any) {
      if (typeof o.m === "function") return 1;
      return -1;
    }

    // f7: method return value is constrained
    f7(o: any) {
      if (o.m() === 42) return 1;
      return -1;
    }

    // f8: rejects null/undefined via == null
    f8(o: any) {
      if (o.x == null) return -1;
      return 0;
    }

    // f9: nested field requirement
    f9(o: any) {
      if ("x" in o && o.x && "y" in o.x && o.x.y === true) return 1;
      return -1;
    }
}
