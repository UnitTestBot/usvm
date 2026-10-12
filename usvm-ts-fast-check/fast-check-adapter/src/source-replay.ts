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
    const expression = file.text.slice(arrowBody.getStart(file), arrowBody.getEnd())
    return { source: applyEdits(file.text, [{ start: arrowBody.getStart(file), end: arrowBody.getEnd(),
      text: `{ const ${temporary} = (${expression}); ${marker} = true; return ${temporary}; }` }]), reason: null }
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
  const finalizers: ts.Block[] = []
  for (let ancestor: ts.Node = selected; ancestor !== owner; ancestor = ancestor.parent) {
    const parent = ancestor.parent
    if (ts.isTryStatement(parent) && parent.finallyBlock !== undefined && ancestor !== parent.finallyBlock) {
      finalizers.push(parent.finallyBlock)
    }
  }
  const temporary = uniqueIdentifier(file, name)
  const edits: Edit[] = []
  const visit = (node: ts.Node): void => {
    if (node !== owner && ts.isFunctionLike(node)) return
    if (ts.isReturnStatement(node)) {
      const expression = node.expression === undefined ? null : file.text.slice(node.expression.getStart(file), node.expression.getEnd())
      const value = node === selected ? 'true' : 'false'
      const result = expression === null ? `${marker} = ${value}; return;`
        : `const ${temporary} = (${expression}); ${marker} = ${value}; return ${temporary};`
      edits.push({ start: node.getStart(file), end: node.getEnd(), text: `{ ${result} }` })
      return
    }
    if ((ts.isBreakStatement(node) || ts.isContinueStatement(node)) && exitsFinalizer(node, finalizers)) {
      const original = file.text.slice(node.getStart(file), node.getEnd())
      edits.push({ start: node.getStart(file), end: node.getEnd(), text: `{ ${marker} = false; ${original} }` })
    }
    ts.forEachChild(node, visit)
  }
  visit(owner)
  // Only an exception escaping a finalizer cancels the pending selected return; locally caught throws do not.
  for (const finalizer of finalizers) {
    edits.push({ start: finalizer.getStart(file) + 1, end: finalizer.getStart(file) + 1, text: 'try {' })
    edits.push({ start: finalizer.getEnd() - 1, end: finalizer.getEnd() - 1,
      text: `} catch (${temporary}) { ${marker} = false; throw ${temporary}; }` })
  }
  return applyEdits(file.text, edits)
}

function exitsFinalizer(node: ts.BreakStatement | ts.ContinueStatement, finalizers: ts.Block[]): boolean {
  const enclosing = finalizers.filter((block) => block.pos <= node.pos && block.end >= node.end)
  if (enclosing.length === 0) return false
  let destination: ts.Node | undefined
  for (let ancestor = node.parent; ancestor !== undefined && !ts.isFunctionLike(ancestor); ancestor = ancestor.parent) {
    if (node.label !== undefined) {
      if (ts.isLabeledStatement(ancestor) && ancestor.label.text === node.label.text) { destination = ancestor; break }
    } else if (isLoop(ancestor) || (ts.isBreakStatement(node) && ts.isSwitchStatement(ancestor))) {
      destination = ancestor; break
    }
  }
  return destination !== undefined && enclosing.some((block) => destination!.pos < block.pos || destination!.end > block.end)
}

function isLoop(node: ts.Node): boolean {
  return ts.isWhileStatement(node) || ts.isDoStatement(node) || ts.isForStatement(node)
    || ts.isForInStatement(node) || ts.isForOfStatement(node)
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
