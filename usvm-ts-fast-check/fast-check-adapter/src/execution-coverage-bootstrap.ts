import { pathToFileURL } from 'node:url';

const entryPoint = process.argv[2];
const rawDirectory = process.env.USVM_V8_COVERAGE_DIRECTORY;
if (entryPoint === undefined || rawDirectory === undefined || rawDirectory.length === 0) {
  throw new Error('Coverage bootstrap requires an adapter entry point and coverage directory');
}

// Native coverage setup has already finished with an empty environment value.
// Restore it before tsx is imported: tsx decides whether to embed the original
// sources when its transform module is initialized, before the first import.
process.env.NODE_V8_COVERAGE = rawDirectory;
process.argv.splice(1, 1);

await import(pathToFileURL(entryPoint).href);
