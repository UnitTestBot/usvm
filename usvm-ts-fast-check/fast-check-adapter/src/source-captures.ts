import ts from 'typescript'

export interface SourceCapture {
  name: string
  kind: 'BUILTIN' | 'NUMBER' | 'STRING' | 'BOOLEAN' | 'NULL' | 'UNDEFINED' | 'FUNCTION' | 'OBJECT' | 'UNSUPPORTED'
  value: string | null
  fields: SourceCapture[]
  reason: string | null
  origin: string | null
}

export function inspectSourceCaptures(sourcePath: string, exportName: string, names: string[]): SourceCapture[] {
  const program = ts.createProgram([sourcePath], { target: ts.ScriptTarget.Latest, module: ts.ModuleKind.ESNext })
  const file = program.getSourceFile(sourcePath)
  if (file === undefined) throw new Error('Capture source file is missing')
  const checker = program.getTypeChecker()
  const moduleSymbol = checker.getSymbolAtLocation(file)
  const exported = moduleSymbol === undefined ? undefined
    : checker.getExportsOfModule(moduleSymbol).find((symbol) => symbol.name === exportName)
  const symbol = exported === undefined ? undefined : resolveAlias(checker, exported)
  const owner = symbol === undefined ? undefined : findCallable(file, checker, symbol)
  if (owner === undefined) return names.map((name) => capture(name, 'UNSUPPORTED', null, [], 'Export has no unique callable scope'))

  return names.map((name) => {
    const binding = checker.getSymbolsInScope(owner, ts.SymbolFlags.Value).find((candidate) => candidate.name === name)
    if (binding === undefined) return capture(name, 'UNSUPPORTED', null, [], 'Capture has no source binding')
    const resolved = inspectBinding(name, binding, checker, program, new Set())
    const unsupportedBuiltinValue = resolved.kind === 'BUILTIN' && !usedOnlyAsCallOwner(owner, binding, checker)
    return unsupportedBuiltinValue
      ? capture(name, 'UNSUPPORTED', null, [], 'Builtin capture is used as a runtime value beyond supported call ownership')
      : resolved
  })
}

function findCallable(file: ts.SourceFile, checker: ts.TypeChecker, symbol: ts.Symbol): ts.FunctionLikeDeclaration | undefined {
  const owners: ts.FunctionLikeDeclaration[] = []
  for (const declaration of symbol.declarations ?? []) {
    if (ts.isFunctionDeclaration(declaration) && declaration.body !== undefined) owners.push(declaration)
    if (ts.isVariableDeclaration(declaration) && declaration.initializer !== undefined
      && (ts.isArrowFunction(declaration.initializer) || ts.isFunctionExpression(declaration.initializer))) {
      owners.push(declaration.initializer)
    }
  }
  const visit = (node: ts.Node): void => {
    if (ts.isBinaryExpression(node) && node.operatorToken.kind === ts.SyntaxKind.EqualsToken
      && ts.isIdentifier(node.left) && checker.getSymbolAtLocation(node.left) === symbol
      && (ts.isArrowFunction(node.right) || ts.isFunctionExpression(node.right))) owners.push(node.right)
    ts.forEachChild(node, visit)
  }
  visit(file)
  return owners.length === 1 ? owners[0] : undefined
}

function inspectBinding(name: string, raw: ts.Symbol, checker: ts.TypeChecker, program: ts.Program,
  visited: Set<ts.Symbol>): SourceCapture {
  const symbol = resolveAlias(checker, raw)
  if (visited.has(symbol)) return capture(name, 'UNSUPPORTED', null, [], 'Cyclic capture initializer')
  const declarations = symbol.declarations ?? []
  const fromDefaultLibrary = declarations.length > 0
    && declarations.every((declaration) => program.isSourceFileDefaultLibrary(declaration.getSourceFile()))
  if (fromDefaultLibrary) {
    if (symbol.name === 'Infinity' || symbol.name === 'NaN') return capture(name, 'NUMBER', symbol.name)
    if (symbol.name === 'undefined') return capture(name, 'UNDEFINED')
    return capture(name, 'BUILTIN', symbol.name)
  }
  const declaration = declarations.length === 1 ? declarations[0] : undefined
  if (declaration !== undefined && ts.isFunctionDeclaration(declaration) && declaration.body !== undefined) {
    return capture(name, 'FUNCTION', 'function')
  }
  if (declaration === undefined || !ts.isVariableDeclaration(declaration) || declaration.initializer === undefined
    || !ts.isVariableDeclarationList(declaration.parent) || !(declaration.parent.flags & ts.NodeFlags.Const)) {
    return capture(name, 'UNSUPPORTED', null, [], 'Capture is not an immutable initialized source binding')
  }
  const result = inspectInitializer(name, declaration.initializer, checker, program, new Set([...visited, symbol]))
  const unsafeObject = result.kind === 'OBJECT' && objectRequiresModuleEvaluation(symbol, checker, new Set())
  return unsafeObject
    ? capture(name, 'UNSUPPORTED', null, [], 'Capture object is mutated or escapes during module initialization')
    : result
}

function inspectInitializer(name: string, node: ts.Expression, checker: ts.TypeChecker, program: ts.Program,
  visited: Set<ts.Symbol>): SourceCapture {
  if (ts.isParenthesizedExpression(node) || ts.isAsExpression(node) || ts.isTypeAssertionExpression(node)) {
    return inspectInitializer(name, node.expression, checker, program, visited)
  }
  if (ts.isNumericLiteral(node)) return capture(name, 'NUMBER', String(Number(node.text)))
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) return capture(name, 'STRING', node.text)
  if (node.kind === ts.SyntaxKind.TrueKeyword || node.kind === ts.SyntaxKind.FalseKeyword) {
    return capture(name, 'BOOLEAN', node.kind === ts.SyntaxKind.TrueKeyword ? 'true' : 'false')
  }
  if (node.kind === ts.SyntaxKind.NullKeyword) return capture(name, 'NULL')
  if (ts.isPrefixUnaryExpression(node) && (node.operator === ts.SyntaxKind.PlusToken || node.operator === ts.SyntaxKind.MinusToken)) {
    const operand = inspectInitializer(name, node.operand, checker, program, visited)
    if (operand.kind === 'NUMBER') {
      const value = node.operator === ts.SyntaxKind.MinusToken ? -Number(operand.value) : Number(operand.value)
      return capture(name, 'NUMBER', Object.is(value, -0) ? '-0' : String(value))
    }
  }
  if (ts.isIdentifier(node)) {
    const symbol = checker.getSymbolAtLocation(node)
    if (symbol !== undefined) return inspectBinding(name, symbol, checker, program, visited)
  }
  if (ts.isArrowFunction(node) || ts.isFunctionExpression(node)) {
    return capture(name, 'FUNCTION', ts.isArrowFunction(node) ? 'arrow' : 'function')
  }
  if (ts.isObjectLiteralExpression(node)) {
    const fields: SourceCapture[] = []
    for (const property of node.properties) {
      if (ts.isPropertyAssignment(property) && (ts.isIdentifier(property.name) || ts.isStringLiteral(property.name))) {
        fields.push(inspectInitializer(property.name.text, property.initializer, checker, program, visited))
      } else if (ts.isMethodDeclaration(property) && ts.isIdentifier(property.name) && property.body !== undefined) {
        fields.push(capture(property.name.text, 'FUNCTION', 'method'))
      } else {
        return capture(name, 'UNSUPPORTED', null, [], 'Capture object requires unsupported property initialization')
      }
    }
    const unsupported = fields.find((field) => field.kind === 'UNSUPPORTED')
    return unsupported === undefined ? capture(name, 'OBJECT', null, fields, null, `${node.getSourceFile().fileName}:${node.pos}`)
      : capture(name, 'UNSUPPORTED', null, [], unsupported.reason)
  }
  return capture(name, 'UNSUPPORTED', null, [], 'Capture initializer requires runtime evaluation')
}

function objectRequiresModuleEvaluation(symbol: ts.Symbol, checker: ts.TypeChecker, visited: Set<ts.Symbol>): boolean {
  if (visited.has(symbol)) return false
  visited.add(symbol)
  let unsafe = false
  for (const declaration of symbol.declarations ?? []) {
    const visit = (node: ts.Node): void => {
      if (ts.isFunctionLike(node)) return
      if (ts.isIdentifier(node) && checker.getSymbolAtLocation(node) === symbol) {
        const parent = node.parent
        const ownDeclaration = ts.isVariableDeclaration(parent) && parent.name === node
        const immutableAlias = ts.isVariableDeclaration(parent) && parent.initializer === node
          && ts.isVariableDeclarationList(parent.parent) && !!(parent.parent.flags & ts.NodeFlags.Const)
        if (immutableAlias) {
          const alias = checker.getSymbolAtLocation(parent.name)
          if (alias === undefined || objectRequiresModuleEvaluation(alias, checker, visited)) unsafe = true
        } else if (!ownDeclaration) {
          unsafe = true
        }
      }
      ts.forEachChild(node, visit)
    }
    visit(declaration.getSourceFile())
  }
  return unsafe
}

function usedOnlyAsCallOwner(owner: ts.FunctionLikeDeclaration, binding: ts.Symbol, checker: ts.TypeChecker): boolean {
  let supported = true
  const visit = (node: ts.Node): void => {
    if (ts.isIdentifier(node) && checker.getSymbolAtLocation(node) === binding) {
      const parent = node.parent
      const directCall = (ts.isCallExpression(parent) || ts.isNewExpression(parent)) && parent.expression === node
      const propertyCall = ts.isPropertyAccessExpression(parent) && parent.expression === node
        && ts.isCallExpression(parent.parent) && parent.parent.expression === parent
      if (!directCall && !propertyCall) supported = false
    }
    ts.forEachChild(node, visit)
  }
  visit(owner)
  return supported
}

function resolveAlias(checker: ts.TypeChecker, symbol: ts.Symbol): ts.Symbol {
  return symbol.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(symbol) : symbol
}

function capture(name: string, kind: SourceCapture['kind'], value: string | null = null,
  fields: SourceCapture[] = [], reason: string | null = null, origin: string | null = null): SourceCapture {
  return { name, kind, value, fields, reason, origin }
}
