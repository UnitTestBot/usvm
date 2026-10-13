import { readFileSync, writeFileSync } from 'node:fs'
import ts from 'typescript'
import { instrumentSourceReplay } from './source-replay.js'
import { inspectSourceCaptures } from './source-captures.js'
import { inspectLocalSourceClosure } from './local-source-closure.js'

const [operation, sourcePath, ...arguments_] = process.argv.slice(2)
if (sourcePath === undefined) throw new Error('Expected a TypeScript source path')
if (operation === 'inspect-local-closure') {
  const entryPath = arguments_[0]
  if (entryPath === undefined) throw new Error('Expected an entry source path')
  writeFileSync(1, JSON.stringify(inspectLocalSourceClosure(sourcePath, entryPath)))
} else {
  const source = readFileSync(sourcePath, 'utf8')
  const sourceFile = ts.createSourceFile(sourcePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const diagnostics = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.Latest },
    fileName: sourcePath,
    reportDiagnostics: true,
  }).diagnostics?.filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error) ?? []
  if (diagnostics.length > 0) throw new Error(ts.flattenDiagnosticMessageText(diagnostics[0]?.messageText ?? '', '\n'))

  if (operation === 'inspect-captures') {
    const [exportName, namesText] = arguments_
    if (exportName === undefined || namesText === undefined) throw new Error('Expected export and capture names')
    const names: unknown = JSON.parse(namesText)
    if (!Array.isArray(names) || !names.every((name) => typeof name === 'string')) throw new Error('Invalid capture names')
    process.stdout.write(JSON.stringify(inspectSourceCaptures(sourcePath, exportName, names as string[])))
  } else if (operation === 'expression-arrow') {
    const [exportName, startText, endText] = arguments_
    const start = Number(startText)
    const end = Number(endText)
    const localExports = sourceFile.statements.filter(ts.isExportDeclaration).flatMap((statement) => {
      if (statement.moduleSpecifier !== undefined || statement.isTypeOnly
        || statement.exportClause === undefined || !ts.isNamedExports(statement.exportClause)) return []
      return statement.exportClause.elements.filter((element) => !element.isTypeOnly && element.name.text === exportName)
        .map((element) => (element.propertyName ?? element.name).text)
    })
    const matches = sourceFile.statements.filter(ts.isVariableStatement).flatMap((statement) => {
      const exported = statement.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword)
      return statement.declarationList.declarations.filter((declaration) =>
        ts.isIdentifier(declaration.name)
        && ((exported && declaration.name.text === exportName) || localExports.includes(declaration.name.text))
        && declaration.initializer !== undefined && ts.isArrowFunction(declaration.initializer)
        && !ts.isBlock(declaration.initializer.body)
        && declaration.initializer.body.getStart(sourceFile) === start && declaration.initializer.body.getEnd() === end,
      )
    })
    process.stdout.write(matches.length === 1 ? 'true' : 'false')
  } else if (operation === 'instrument-replay') {
    const [mode, startText, endText, expressionStartText, expressionEndText, marker] = arguments_
    if ((mode !== 'ENTRY' && mode !== 'COMPLETED_RETURN') || marker === undefined) {
      throw new Error('Expected replay mode, offsets and marker')
    }
    const result = instrumentSourceReplay(sourceFile, {
      mode,
      start: Number(startText),
      end: Number(endText),
      expressionStart: expressionStartText === '-' ? null : Number(expressionStartText),
      expressionEnd: expressionEndText === '-' ? null : Number(expressionEndText),
      marker,
    })
    process.stdout.write(JSON.stringify(result))
  } else {
    throw new Error(`Unknown source inspection operation: ${operation}`)
  }
}
