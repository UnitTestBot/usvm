declare module '@bcoe/v8-coverage' {
  import type { Profiler } from 'node:inspector';

  export function mergeProcessCovs(reports: { result: Profiler.ScriptCoverage[] }[]): {
    result: Profiler.ScriptCoverage[];
  };
}
