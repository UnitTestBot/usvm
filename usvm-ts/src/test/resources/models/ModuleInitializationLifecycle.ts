const box = { value: 10 };
const alias = box;

export class ModuleInitializationLifecycle {
  readTwice(): number {
    const before = box.value;
    alias.value = before + 1;
    return before * 100 + box.value;
  }
}
