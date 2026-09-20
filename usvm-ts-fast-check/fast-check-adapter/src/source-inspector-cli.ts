import { readFileSync } from 'node:fs'
import ts from 'typescript'

function fail(message: string): never {
  process.stderr.write(`${message}\n`)
  process.exit(2)
}

const [sourcePath, exportName, startText, endText] = process.argv.slice(2)
if (sourcePath === undefined || exportName === undefined || startText === undefined || endText === undefined) {
  fail('Expected source path, export name, start offset, and end offset')
}

const startOffset = Number(startText)
const endOffset = Number(endText)
if (!Number.isSafeInteger(startOffset) || !Number.isSafeInteger(endOffset)) {
  fail('Source offsets must be safe integers')
}

const source = readFileSync(sourcePath, 'utf8')
const diagnostics = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.Latest },
  fileName: sourcePath,
  reportDiagnostics: true,
}).diagnostics?.filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error) ?? []
if (diagnostics.length > 0) {
  fail(`Cannot parse TypeScript source: ${ts.flattenDiagnosticMessageText(diagnostics[0]?.messageText ?? '', '\n')}`)
}

const sourceFile = ts.createSourceFile(sourcePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)

const bodies: ts.ConciseBody[] = []
for (const statement of sourceFile.statements) {
  if (!ts.isVariableStatement(statement)) continue
  const exported = statement.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword) === true
  if (!exported) continue

  for (const declaration of statement.declarationList.declarations) {
    if (!ts.isIdentifier(declaration.name) || declaration.name.text !== exportName) continue
    if (!declaration.initializer || !ts.isArrowFunction(declaration.initializer)) continue
    if (ts.isBlock(declaration.initializer.body)) continue

    bodies.push(declaration.initializer.body)
  }
}

const matchingBodies = bodies.filter((body) =>
  body.getStart(sourceFile, false) === startOffset && body.getEnd() === endOffset,
)
process.stdout.write(matchingBodies.length === 1 ? 'true' : 'false')
