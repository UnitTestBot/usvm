import ts from 'typescript'

type InitializerKind = 'primitive' | 'reference'

/** Check allocations and aliases without evaluating source or reconstructing values. */
export function unsupportedModuleInitialization(
  source: ts.SourceFile,
  checker: ts.TypeChecker,
  closure: Set<ts.SourceFile>,
): ts.Statement | undefined {
  const initializer = (expression: ts.Expression, seen = new Set<ts.Node>()): InitializerKind | undefined => {
    if (seen.has(expression)) return undefined
    const next = new Set(seen).add(expression)

    if (ts.isParenthesizedExpression(expression) || ts.isAsExpression(expression)
      || ts.isTypeAssertionExpression(expression) || ts.isSatisfiesExpression(expression)
      || ts.isNonNullExpression(expression)) return initializer(expression.expression, next)
    if (ts.isArrowFunction(expression) || ts.isFunctionExpression(expression)
      || ts.isRegularExpressionLiteral(expression)) return 'reference'
    if (ts.isLiteralExpression(expression) || expression.kind === ts.SyntaxKind.TrueKeyword
      || expression.kind === ts.SyntaxKind.FalseKeyword || expression.kind === ts.SyntaxKind.NullKeyword) return 'primitive'
    if (ts.isPrefixUnaryExpression(expression)) {
      const operand = initializer(expression.operand, next)
      if (operand === undefined) return undefined
      if (expression.operator === ts.SyntaxKind.ExclamationToken) return 'primitive'
      // Numeric coercion of a reference could invoke valueOf/toString and arbitrary effects.
      const numericOperator = [ts.SyntaxKind.PlusToken, ts.SyntaxKind.MinusToken, ts.SyntaxKind.TildeToken]
        .includes(expression.operator)
      return numericOperator && operand === 'primitive' ? 'primitive' : undefined
    }
    if (ts.isArrayLiteralExpression(expression)) {
      const pureElements = expression.elements.every(element => !ts.isSpreadElement(element)
        && !ts.isOmittedExpression(element) && initializer(element, next) !== undefined)
      return pureElements ? 'reference' : undefined
    }
    if (ts.isObjectLiteralExpression(expression)) {
      const pureProperties = expression.properties.every(property => {
        if (!ts.isPropertyAssignment(property) && !ts.isShorthandPropertyAssignment(property)) return false
        const staticName = ts.isIdentifier(property.name) || ts.isStringLiteral(property.name)
          || ts.isNumericLiteral(property.name)
        const prototypeKey = (ts.isIdentifier(property.name) || ts.isStringLiteral(property.name))
          && property.name.text === '__proto__'
        if (!staticName || prototypeKey) return false
        if (ts.isShorthandPropertyAssignment(property)) {
          if (property.objectAssignmentInitializer) return false
          return constant(checker.getShorthandAssignmentValueSymbol(property), property.name, next) !== undefined
        }
        return initializer(property.initializer, next) !== undefined
      })
      return pureProperties ? 'reference' : undefined
    }
    if (!ts.isIdentifier(expression)) return undefined
    return constant(checker.getSymbolAtLocation(expression), expression, next)
  }

  const constant = (binding: ts.Symbol | undefined, use: ts.Node, seen: Set<ts.Node>): InitializerKind | undefined => {
    if (!binding) return undefined
    const symbol = binding.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(binding) : binding
    const declarations = symbol.declarations ?? []
    if (declarations.length !== 1) return undefined
    const declaration = declarations[0]!
    if (!closure.has(declaration.getSourceFile())) return undefined
    if (ts.isFunctionDeclaration(declaration)) return declaration.body ? 'reference' : undefined
    if (!ts.isVariableDeclaration(declaration) || !declaration.initializer
      || !ts.isVariableDeclarationList(declaration.parent)
      || !(declaration.parent.flags & ts.NodeFlags.Const)) return undefined
    const declaredBeforeUse = declaration.getSourceFile() !== use.getSourceFile() || declaration.end < use.getStart()
    return declaredBeforeUse ? initializer(declaration.initializer, seen) : undefined
  }

  const supported = (statement: ts.Statement): boolean => {
    if (ts.isImportDeclaration(statement) || ts.isExportDeclaration(statement)
      || ts.isFunctionDeclaration(statement) || ts.isInterfaceDeclaration(statement)
      || ts.isTypeAliasDeclaration(statement) || ts.isEmptyStatement(statement)) return true
    if (ts.isImportEqualsDeclaration(statement)) return statement.isTypeOnly
    if (ts.isExpressionStatement(statement) && ts.isStringLiteral(statement.expression)) return true
    if (ts.isVariableStatement(statement)) {
      return statement.declarationList.declarations.every(declaration => ts.isIdentifier(declaration.name)
        && (!declaration.initializer || initializer(declaration.initializer) !== undefined))
    }
    if (ts.isExportAssignment(statement)) return !statement.isExportEquals && initializer(statement.expression) !== undefined
    if (ts.isClassDeclaration(statement)) {
      const extendsRuntimeBase = statement.heritageClauses?.some(clause => clause.token === ts.SyntaxKind.ExtendsKeyword) ?? false
      const decorated = (ts.getDecorators(statement)?.length ?? 0) > 0
      if (extendsRuntimeBase || decorated) return false
      return statement.members.every(member => !ts.isClassStaticBlockDeclaration(member)
        && !(member.name && ts.isComputedPropertyName(member.name))
        && !(ts.canHaveDecorators(member) && ts.getDecorators(member)?.length)
        && !(ts.isPropertyDeclaration(member) && member.initializer
          && member.modifiers?.some(modifier => modifier.kind === ts.SyntaxKind.StaticKeyword)))
    }
    if (ts.isEnumDeclaration(statement)) {
      return statement.members.every(member => !member.initializer || ts.isLiteralExpression(member.initializer))
    }
    return false
  }

  return source.statements.find(statement => !supported(statement))
}
