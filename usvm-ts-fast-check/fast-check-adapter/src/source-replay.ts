import ts from 'typescript'

export interface SourceReplayTarget {
  mode: 'ENTRY' | 'COMPLETED_RETURN'
  start: number
  end: number
  expressionStart: number | null
  expressionEnd: number | null
  marker: string
}

interface Edit { start: number; end: number; text: string }
export interface SourceReplayInstrumentation { source: string | null; reason: string | null }

export function instrumentSourceReplay(file: ts.SourceFile, target: SourceReplayTarget): SourceReplayInstrumentation {
  const nodes: ts.Node[] = []
  const collect = (node: ts.Node): void => {
    const matchesFrozenRange = node.getStart(file) === target.start && node.getEnd() <= target.end
      && triviaOnly(file.text.slice(node.getEnd(), target.end))
    if (matchesFrozenRange) nodes.push(node)
    ts.forEachChild(node, collect)
  }
  collect(file)
  const statement = nodes.find(ts.isStatement)
  const arrowBody = nodes.find((node) => ts.isArrowFunction(node.parent) && node.parent.body === node)
  const marker = `(globalThis as Record<string, unknown>)[${JSON.stringify(target.marker)}]`

  if (target.mode === 'ENTRY') {
    if (statement !== undefined) return { source: instrumentEntry(file, statement, marker), reason: null }
    if (arrowBody !== undefined && !ts.isBlock(arrowBody)) {
      const expression = file.text.slice(arrowBody.getStart(file), arrowBody.getEnd())
      return { source: applyEdits(file.text, [{ start: arrowBody.getStart(file), end: arrowBody.getEnd(),
        text: `(${marker} = true, (${expression}))` }]), reason: null }
    }
    return { source: null, reason: 'Source target does not identify an exact statement or arrow body' }
  }

  if (statement !== undefined && ts.isReturnStatement(statement)) {
    const expression = statement.expression
    const expressionMatches = expression === undefined
      ? target.expressionStart === null && target.expressionEnd === null
      : expression.getStart(file) === target.expressionStart && expression.getEnd() === target.expressionEnd
    if (!expressionMatches) return { source: null, reason: 'Return expression does not match its frozen AST range' }
    return { source: instrumentCompletedReturn(file, statement, marker, target.marker), reason: null }
  }
  const matchesArrowExpression = arrowBody !== undefined && !ts.isBlock(arrowBody)
    && target.expressionStart === arrowBody.getStart(file) && target.expressionEnd === arrowBody.getEnd()
  if (matchesArrowExpression && arrowBody !== undefined) {
    const temporary = uniqueIdentifier(file, target.marker)
    const depth = depthStorage(target.marker)
    const expression = file.text.slice(arrowBody.getStart(file), arrowBody.getEnd())
    return { source: applyEdits(file.text, [{ start: arrowBody.getStart(file), end: arrowBody.getEnd(),
      text: `{ ${depth} = (${depth} ?? 0) + 1; try { const ${temporary} = (${expression}); `
        + `if (${depth} === 1) ${marker} = true; return ${temporary}; } finally { ${depth}--; } }` }]), reason: null }
  }
  return { source: null, reason: 'Completed-return target does not identify an exact return or arrow body' }
}

function triviaOnly(text: string): boolean {
  const scanner = ts.createScanner(ts.ScriptTarget.Latest, false, ts.LanguageVariant.Standard, text)
  for (let kind = scanner.scan(); kind !== ts.SyntaxKind.EndOfFileToken; kind = scanner.scan()) {
    if (kind !== ts.SyntaxKind.WhitespaceTrivia && kind !== ts.SyntaxKind.NewLineTrivia
      && kind !== ts.SyntaxKind.SingleLineCommentTrivia && kind !== ts.SyntaxKind.MultiLineCommentTrivia) return false
  }
  return true
}

function instrumentEntry(file: ts.SourceFile, statement: ts.Statement, marker: string): string {
  let anchor = statement
  // Keep labels attached to their iteration statement, so continue label still names a loop.
  while (ts.isLabeledStatement(anchor.parent)) anchor = anchor.parent
  const parent = anchor.parent
  const statementList = ts.isSourceFile(parent) || ts.isBlock(parent) || ts.isCaseClause(parent) || ts.isDefaultClause(parent)
  const markerStatement = `${marker} = true;\n`
  if (statementList) {
    const directives = parent.statements.filter((candidate, index, statements) =>
      ts.isExpressionStatement(candidate) && ts.isStringLiteral(candidate.expression)
      && statements.slice(0, index).every((previous) => ts.isExpressionStatement(previous) && ts.isStringLiteral(previous.expression)),
    )
    if (directives.includes(anchor)) {
      const end = directives[directives.length - 1]!.getEnd()
      return applyEdits(file.text, [{ start: end, end, text: `\n${markerStatement}` }])
    }
    return applyEdits(file.text, [{ start: anchor.getStart(file), end: anchor.getStart(file), text: markerStatement }])
  }
  const original = file.text.slice(anchor.getStart(file), anchor.getEnd())
  return applyEdits(file.text, [{ start: anchor.getStart(file), end: anchor.getEnd(), text: `{ ${markerStatement}${original} }` }])
}

function instrumentCompletedReturn(file: ts.SourceFile, selected: ts.ReturnStatement, marker: string, name: string): string {
  let owner: ts.Node = selected.parent
  while (!ts.isFunctionLike(owner)) owner = owner.parent
  const body = functionBody(owner)
  if (body === undefined) throw new Error('Completed return must belong to a function body')
  const depth = depthStorage(name)
  const temporary = uniqueIdentifier(file, name)
  const edits: Edit[] = []
  const visit = (node: ts.Node): void => {
    if (node !== owner && ts.isFunctionLike(node)) return
    if (ts.isReturnStatement(node)) {
      const expression = node.expression === undefined ? null : file.text.slice(node.expression.getStart(file), node.expression.getEnd())
      const value = node === selected ? 'true' : 'false'
      const completion = `if (${depth} === 1) ${marker} = ${value};`
      const result = expression === null ? `${completion} return;`
        : `const ${temporary} = (${expression}); ${completion} return ${temporary};`
      edits.push({ start: node.getStart(file), end: node.getEnd(), text: `{ ${result} }` })
      return
    }
    ts.forEachChild(node, visit)
  }
  visit(owner)
  const firstBodyStatement = body.statements.find((statement) =>
    !ts.isExpressionStatement(statement) || !ts.isStringLiteral(statement.expression),
  )
  const guardedStart = firstBodyStatement?.getFullStart() ?? body.getEnd() - 1
  edits.push({ start: guardedStart, end: guardedStart, text: `\n${depth} = (${depth} ?? 0) + 1; try {\n` })
  edits.push({ start: body.getEnd() - 1, end: body.getEnd() - 1,
    text: `\nif (${depth} === 1) ${marker} = false; } finally { ${depth}--; }\n` })
  return applyEdits(file.text, edits)
}

function functionBody(node: ts.Node): ts.Block | undefined {
  if (ts.isFunctionDeclaration(node) || ts.isFunctionExpression(node) || ts.isMethodDeclaration(node)
    || ts.isGetAccessorDeclaration(node) || ts.isSetAccessorDeclaration(node) || ts.isConstructorDeclaration(node)) {
    return node.body
  }
  if (ts.isArrowFunction(node) && ts.isBlock(node.body)) return node.body
  return undefined
}

function depthStorage(marker: string): string {
  return `(globalThis as Record<string, number>)[${JSON.stringify(`${marker}:depth`)}]`
}

function uniqueIdentifier(file: ts.SourceFile, marker: string): string {
  let name = `__usvm_${marker.replace(/[^a-zA-Z0-9_]/g, '_')}_value`
  while (file.text.includes(name)) name += '_'
  return name
}

function applyEdits(source: string, edits: Edit[]): string {
  const ordered = edits.sort((left, right) => right.start - left.start || right.end - left.end)
  let result = source
  for (const edit of ordered) result = result.slice(0, edit.start) + edit.text + result.slice(edit.end)
  return result
}
