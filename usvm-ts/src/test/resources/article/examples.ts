class Example {
    // f1: "x" must exist and be number
    f1(o: any) {
      if ("x" in o && typeof o.x === "number") return 1;
      throw new Error("bad");
    }

    // f2: throws if "x" is missing
    f2(o: any) {
      if (!("x" in o)) throw new Error("miss");
      return 0;
    }

    // f3a: require number explicitly, still use '+' in the branch
    f3a(o: any) {
      if (typeof o.x === "number" && o.x + 1 > 0) return 1;
      throw new Error("not-number-branch");
    }

    // f3b: '+' coerces many values to number — beware!
    f3b(o: any) {
      const y = o.x + 1;
      if (typeof y === "number") return 1;
      throw new Error("string-branch");
    }

    // f4: writes then deletes; checks absence
    f4(o: any) {
      o.x = 1;            // создаётся *внутри*, не часть входа
      delete o.x;
      if ("x" in o) throw new Error("still here");
      return 0;
    }

    // f5: destructuring with default
    f5({ xx = 1 }: { xx?: number }) {
      if (xx > 0) return 1;
      throw new Error("non-positive");
    }

    // f6: discriminated union
    type A = { kind: "A"; a: number };
    type B = { kind: "B"; b: string };
    f6(o: A | B) {
      if (o.kind === "A" && o.a > 0) return 1;
      throw new Error("not A>0");
    }

    // f7: method presence only
    f7(o: any) {
      if (typeof o.m === "function") return 1;
      throw new Error("no method");
    }

    // f8: method return value is constrained
    f8(o: any) {
      if (o.m() === 42) return 1;
      throw new Error("bad ret");
    }

    // f9: rejects null/undefined via == null
    f9(o: any) {
      if (o.x == null) throw new Error("nullish");
      return 0;
    }

    // f10: nested field requirement
    f10(o: any) {
      if ("x" in o && o.x && "y" in o.x && o.x.y === true) return 1;
      throw new Error("missing nested");
    }
}
