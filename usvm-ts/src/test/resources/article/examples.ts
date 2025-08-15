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

    // f3: x: number|string; numeric branch
    f3(o: any) {
      const y = o.x + 1;             // если x число → number, если строка → string
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
    f5({ x = 1 }: { x?: number }) {
      if (x > 0) return 1;
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
