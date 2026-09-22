import { readFileSync } from 'node:fs'
import ts from 'typescript'

const INSPECT_COMPLETED_RETURN = 'inspect-completed-return'
const INSTRUMENT_COMPLETED_RETURN = 'instrument-completed-return'
const NONE = '-'

function fail(message: string): never {
  process.stderr.write(`${message}\n`)
  process.exit(2)
}

function parseOffset(text: string | undefined, label: string): number {
  const offset = Number(text)
  if (!Number.isSafeInteger(offset)) fail(`${label} must be a safe integer`)
  return offset
}

function parseOptionalOffset(text: string | undefined, label: string): number | undefined {
  if (text === NONE) return undefined
  return parseOffset(text, label)
}

const [operation, sourcePath, exportName, targetStartText, targetEndText, expressionStartText, expressionEndText, marker] =
  process.argv.slice(2)
if (
  operation === undefined || sourcePath === undefined || exportName === undefined || targetStartText === undefined ||
  targetEndText === undefined || expressionStartText === undefined || expressionEndText === undefined
) {
  fail('Expected operation, source path, export name, target offsets, and expression offsets')
}
if (operation !== INSPECT_COMPLETED_RETURN && operation !== INSTRUMENT_COMPLETED_RETURN) {
  fail(`Unknown source-inspector operation: ${operation}`)
}
if (operation === INSTRUMENT_COMPLETED_RETURN && marker === undefined) {
  fail('Completed-return instrumentation requires a marker')
}
const requiredSourcePath = sourcePath ?? fail('Missing source path')
const requiredExportName = exportName ?? fail('Missing export name')

const targetStart = parseOffset(targetStartText, 'Target start offset')
const targetEnd = parseOffset(targetEndText, 'Target end offset')
const expressionStart = parseOptionalOffset(expressionStartText, 'Expression start offset')
const expressionEnd = parseOptionalOffset(expressionEndText, 'Expression end offset')
if ((expressionStart === undefined) !== (expressionEnd === undefined)) {
  fail('Expression offsets must be both present or both absent')
}

const source = readFileSync(requiredSourcePath, 'utf8')
const diagnostics = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.Latest },
  fileName: requiredSourcePath,
  reportDiagnostics: true,
}).diagnostics?.filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error) ?? []
if (diagnostics.length > 0) {
  fail(`Cannot parse TypeScript source: ${ts.flattenDiagnosticMessageText(diagnostics[0]?.messageText ?? '', '\n')}`)
}

const sourceFile = ts.createSourceFile(requiredSourcePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)

type SupportedCallable = ts.FunctionDeclaration | ts.ArrowFunction

interface CallableOwner {
  callable: SupportedCallable
  body: ts.ConciseBody
  declaration: ts.Statement
}

interface CompletedReturnTarget {
  kind: 'expression-arrow' | 'return-statement'
  owner: CallableOwner
  returnStatement?: ts.ReturnStatement
}

interface Edit {
  start: number
  end: number
  replacement: string
}

function hasExportModifier(node: ts.Node & { modifiers?: ts.NodeArray<ts.ModifierLike> }): boolean {
  return node.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword) === true
}

function exportedLocalNames(name: string): Set<string> {
  const names = new Set<string>()

  for (const statement of sourceFile.statements) {
    if (ts.isFunctionDeclaration(statement) && statement.name?.text === name && hasExportModifier(statement)) {
      names.add(name)
    }
    if (ts.isVariableStatement(statement) && hasExportModifier(statement)) {
      if (statement.declarationList.declarations.some((declaration) =>
        ts.isIdentifier(declaration.name) && declaration.name.text === name
      )) {
        names.add(name)
      }
    }
    if (
      ts.isExportDeclaration(statement) && !statement.isTypeOnly && statement.moduleSpecifier === undefined &&
      statement.exportClause !== undefined && ts.isNamedExports(statement.exportClause)
    ) {
      for (const specifier of statement.exportClause.elements) {
        if (!specifier.isTypeOnly && specifier.name.text === name) {
          names.add(specifier.propertyName?.text ?? specifier.name.text)
        }
      }
    }
  }

  return names
}

function exportedCallableOwners(name: string): CallableOwner[] {
  const localNames = exportedLocalNames(name)
  const owners: CallableOwner[] = []

  for (const statement of sourceFile.statements) {
    if (
      ts.isFunctionDeclaration(statement) && statement.body !== undefined && statement.name !== undefined &&
      localNames.has(statement.name.text)
    ) {
      owners.push({ callable: statement, body: statement.body, declaration: statement })
    }
    if (!ts.isVariableStatement(statement)) continue

    for (const declaration of statement.declarationList.declarations) {
      if (!ts.isIdentifier(declaration.name) || !localNames.has(declaration.name.text)) continue
      if (declaration.initializer === undefined || !ts.isArrowFunction(declaration.initializer)) continue

      owners.push({ callable: declaration.initializer, body: declaration.initializer.body, declaration: statement })
    }
  }

  return owners.filter((owner, index) => owners.findIndex((candidate) => candidate.callable === owner.callable) === index)
}

function nestedFunction(node: ts.Node, owner: SupportedCallable): boolean {
  return node !== owner && ts.isFunctionLike(node)
}

function returnStatements(owner: CallableOwner): ts.ReturnStatement[] {
  if (!ts.isBlock(owner.body)) return []

  const returns: ts.ReturnStatement[] = []
  function visit(node: ts.Node): void {
    if (nestedFunction(node, owner.callable)) return
    if (ts.isReturnStatement(node)) {
      returns.push(node)
      return
    }
    ts.forEachChild(node, visit)
  }
  visit(owner.body)

  return returns
}

function onlyTriviaAndOptionalSemicolon(start: number, end: number): boolean {
  if (start > end) return false
  const scanner = ts.createScanner(
    ts.ScriptTarget.Latest,
    true,
    ts.LanguageVariant.Standard,
    source.slice(start, end),
  )
  let semicolons = 0
  while (true) {
    const token = scanner.scan()
    if (token === ts.SyntaxKind.EndOfFileToken) return true
    if (token !== ts.SyntaxKind.SemicolonToken || ++semicolons > 1) return false
  }
}

function matchesReturnStatement(statement: ts.ReturnStatement): boolean {
  if (statement.getStart(sourceFile, false) !== targetStart) return false

  const expression = statement.expression
  if (expression === undefined) {
    if (expressionStart !== undefined || expressionEnd !== undefined) return false
    const returnKeywordEnd = targetStart + 'return'.length
    return targetEnd >= returnKeywordEnd && onlyTriviaAndOptionalSemicolon(returnKeywordEnd, targetEnd)
  }
  if (expressionStart === undefined || expressionEnd === undefined) return false
  if (expression.getStart(sourceFile, false) !== expressionStart || expression.getEnd() !== expressionEnd) return false

  return targetEnd >= expressionEnd && onlyTriviaAndOptionalSemicolon(expressionEnd, targetEnd)
}

function findCompletedReturnTarget(): CompletedReturnTarget | undefined {
  const owners = exportedCallableOwners(requiredExportName)
  if (owners.length !== 1) return undefined
  const owner = owners[0]
  if (owner === undefined) return undefined

  if (!ts.isBlock(owner.body)) {
    const body = owner.body
    if (
      body.getStart(sourceFile, false) === targetStart && body.getEnd() === targetEnd &&
      expressionStart === targetStart && expressionEnd === targetEnd
    ) {
      return { kind: 'expression-arrow', owner }
    }
    return undefined
  }

  const matches = returnStatements(owner).filter(matchesReturnStatement)
  const matchingReturn = matches[0]
  return matches.length === 1 && matchingReturn !== undefined
    ? { kind: 'return-statement', owner, returnStatement: matchingReturn }
    : undefined
}

function completionExpression(expression: ts.Expression, depthName: string, markerName: string, hit: boolean): string {
  const valueName = `${markerName}_value`
  const expressionText = source.slice(expression.getStart(sourceFile, false), expression.getEnd())

  return `(((${valueName}: any) => { if (${depthName} === 1) ` +
    `(globalThis as Record<string, unknown>)[${JSON.stringify(markerName)}] = ${hit}; ` +
    `return ${valueName}; })((${expressionText})))`
}

function instrumentReturn(
  statement: ts.ReturnStatement,
  target: ts.ReturnStatement,
  depthName: string,
  markerName: string,
): Edit {
  const hit = statement === target
  if (statement.expression !== undefined) {
    return {
      start: statement.expression.getStart(sourceFile, false),
      end: statement.expression.getEnd(),
      replacement: completionExpression(statement.expression, depthName, markerName, hit),
    }
  }

  return {
    start: statement.getStart(sourceFile, false),
    end: statement.getEnd(),
    replacement: `{ if (${depthName} === 1) ` +
      `(globalThis as Record<string, unknown>)[${JSON.stringify(markerName)}] = ${hit}; return; }`,
  }
}

function firstNonDirectiveOffset(body: ts.Block): number {
  const firstNonDirective = body.statements.find((statement) =>
    !ts.isExpressionStatement(statement) || !ts.isStringLiteral(statement.expression)
  )
  return firstNonDirective?.getFullStart() ?? body.getEnd() - 1
}

function applyEdits(edits: Edit[]): string {
  const ordered = [...edits].sort((left, right) => right.start - left.start || right.end - left.end)
  let result = source
  let previousStart = source.length
  for (const edit of ordered) {
    if (edit.start < 0 || edit.end < edit.start || edit.end > previousStart) {
      fail('Source-inspector edits overlap or escape the source file')
    }
    result = result.slice(0, edit.start) + edit.replacement + result.slice(edit.end)
    previousStart = edit.start
  }
  return result
}

function instrumentCompletedReturn(target: CompletedReturnTarget, markerName: string): string {
  const depthName = `${markerName}_depth`
  const declarationOffset = target.owner.declaration.getStart(sourceFile, false)
  const edits: Edit[] = [{ start: declarationOffset, end: declarationOffset, replacement: `let ${depthName} = 0;\n` }]
  const body = target.owner.body

  if (!ts.isBlock(body)) {
    const expression = body
    edits.push({
      start: expression.getStart(sourceFile, false),
      end: expression.getEnd(),
      replacement: `{ ${depthName}++; try { return ${completionExpression(expression, depthName, markerName, true)}; ` +
        `} finally { ${depthName}--; } }`,
    })
    return applyEdits(edits)
  }

  const selectedReturn = target.returnStatement
  if (selectedReturn === undefined) fail('Return-statement target is missing its return statement')
  for (const statement of returnStatements(target.owner)) {
    edits.push(instrumentReturn(statement, selectedReturn, depthName, markerName))
  }

  const guardedBodyStart = firstNonDirectiveOffset(body)
  edits.push({
    start: guardedBodyStart,
    end: guardedBodyStart,
    replacement: `${depthName}++; try {\n`,
  })
  edits.push({
    start: body.getEnd() - 1,
    end: body.getEnd() - 1,
    replacement: `\nif (${depthName} === 1) ` +
      `(globalThis as Record<string, unknown>)[${JSON.stringify(markerName)}] = false; ` +
      `} finally { ${depthName}--; }\n`,
  })

  return applyEdits(edits)
}

const target = findCompletedReturnTarget()
if (operation === INSPECT_COMPLETED_RETURN) {
  process.stdout.write(target?.kind ?? 'unsupported')
} else if (target === undefined) {
  process.stdout.write('unsupported\n')
} else {
  process.stdout.write(`ok\n${instrumentCompletedReturn(target, marker ?? fail('Missing marker'))}`)
}
