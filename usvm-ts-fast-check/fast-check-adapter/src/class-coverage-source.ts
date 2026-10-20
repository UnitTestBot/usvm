import { createHash } from 'node:crypto'
import ts from 'typescript'

export interface ClassStatementDescriptor {
  id: string
  memberName: string
  kind: string
  startOffset: number
  endOffset: number
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

export interface ClassCallableDescriptor {
  name: string
  kind: 'METHOD' | 'GETTER' | 'SETTER' | 'FUNCTION_FIELD'
  isStatic: boolean
  parameterNames: string[]
  parameterTypeTexts: (string | null)[]
  resultTypeText: string | null
}

export interface ClassStateFieldDescriptor { name: string; isEcmaPrivate: boolean; typeText: string | null }
export interface ClassDataClassDescriptor { exportName: string; fields: string[] }
export interface ClassSurfaceInventory {
  constructorParameterNames: string[]
  publicMembers: ClassCallableDescriptor[]
  instanceDataFields: ClassStateFieldDescriptor[]
  dataClasses: ClassDataClassDescriptor[]
}

export interface ClassCoverageInstrumentation {
  source: string | null
  reason: string | null
  sourceSha256: string
  instrumentedSha256: string | null
  counterGlobalName: string
  instrumenterVersion: string
  statements: ClassStatementDescriptor[]
  inventory: ClassSurfaceInventory | null
}

interface MemberBody { name: string; node: ts.ClassElement; body: ts.Node; publicSurface: boolean; isStatic: boolean }
interface Insertion { offset: number; text: string; order: number }
const VERSION = 'class-statements-v2'

/** Exact original AST IDs. Native counters avoid c8's ambiguous same-line statement records. */
export function instrumentClassCoverage(file: ts.SourceFile, className: string): ClassCoverageInstrumentation {
  const sourceSha256 = hash(file.text)
  const counterGlobalName = `__usvmCallsClassStatementHits_${hash(`${sourceSha256}:${className}`).slice(0, 24)}`
  const base = { sourceSha256, counterGlobalName, instrumenterVersion: VERSION }
  const unsupported = (reason: string): ClassCoverageInstrumentation =>
    ({ ...base, source: null, reason, instrumentedSha256: null, statements: [], inventory: null })
  const matches = file.statements.filter(ts.isClassDeclaration).filter((node) => node.name?.text === className)
  if (matches.length !== 1) return unsupported(`Expected one top-level named class ${className}`)
  const selected = matches[0]!
  if (selected.heritageClauses?.some((clause) => clause.token === ts.SyntaxKind.ExtendsKeyword)) {
    return unsupported('Inherited class lifecycle is outside the checked class statement subset')
  }
  const decorated = ts.canHaveDecorators(selected) && (ts.getDecorators(selected)?.length ?? 0) > 0
  if (decorated) return unsupported('Decorated classes are outside the checked class statement subset')

  const members: MemberBody[] = []
  const initializers: MemberBody[] = []
  for (const member of selected.members) {
    const modifiers = ts.canHaveModifiers(member) ? ts.getModifiers(member) ?? [] : []
    const isStatic = modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.StaticKeyword)
    const memberDecorated = ts.canHaveDecorators(member) && (ts.getDecorators(member)?.length ?? 0) > 0
    if (memberDecorated) return unsupported('Decorated members are outside the checked class statement subset')
    if (ts.isClassStaticBlockDeclaration(member)) return unsupported('Static initialization is not constructor coverage')
    if (ts.isIndexSignatureDeclaration(member) || ts.isSemicolonClassElement(member)) continue
    const name = ts.isConstructorDeclaration(member) ? 'constructor' : memberName(member)
    if (name === null) return unsupported('Computed or unnamed class members are outside the checked subset')
    const publicSurface = !modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.PrivateKeyword
      || modifier.kind === ts.SyntaxKind.ProtectedKeyword) && !name.startsWith('#')
    if (ts.isMethodDeclaration(member) || ts.isGetAccessorDeclaration(member)
      || ts.isSetAccessorDeclaration(member) || ts.isConstructorDeclaration(member)) {
      if (member.body === undefined) continue
      const asynchronous = modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.AsyncKeyword)
        || (ts.isMethodDeclaration(member) && member.asteriskToken !== undefined)
      if (asynchronous) return unsupported('Async or generator members are outside the synchronous class subset')
      members.push({ name, node: member, body: member.body, publicSurface, isStatic })
    } else if (ts.isPropertyDeclaration(member) && member.initializer !== undefined) {
      const initializer = member.initializer
      if (ts.isArrowFunction(initializer) || ts.isFunctionExpression(initializer)) {
        const asynchronous = initializer.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.AsyncKeyword)
          || (ts.isFunctionExpression(initializer) && initializer.asteriskToken !== undefined)
        if (asynchronous) return unsupported('Async or generator callable properties are unsupported')
        members.push({ name, node: member, body: initializer.body, publicSurface, isStatic })
      } else {
        if (isStatic) return unsupported('Static property initialization is outside constructor coverage')
        initializers.push({ name, node: member, body: initializer, publicSurface: true, isStatic: false })
      }
    }
  }

  const memberKey = (name: string, isStatic: boolean): string => `${isStatic ? 'static' : 'instance'}:${name}`
  const callableMemberKeys = new Set(selected.members.flatMap((member) => {
    const name = memberName(member)
    const functionProperty = ts.isPropertyDeclaration(member)
      && ((member.initializer !== undefined && (ts.isArrowFunction(member.initializer) || ts.isFunctionExpression(member.initializer)))
        || (member.type !== undefined && ts.isFunctionTypeNode(member.type)))
    if (name === null || (!ts.isMethodDeclaration(member) && !functionProperty)) return []
    const isStatic = ts.canHaveModifiers(member) && (ts.getModifiers(member)?.some((modifier) => modifier.kind === ts.SyntaxKind.StaticKeyword) ?? false)
    return [memberKey(name, isStatic)]
  }))
  const reachable = new Set(members.filter((member) => member.publicSurface).map((member) => memberKey(member.name, member.isStatic)))
  const hasPrivateCallableBodies = members.some((member) => !member.publicSurface)
  const nativeGlobals = new Set(['String', 'Number', 'Boolean', 'parseFloat', 'parseInt', 'isNaN', 'isFinite',
    'encodeURI', 'encodeURIComponent', 'decodeURI', 'decodeURIComponent', 'escape', 'unescape', 'atob', 'btoa'])
  const shadowedNativeGlobals = declaredValueBindings(file)
  let invalidReason: string | null = null
  const localFunctionCache = new Map<ts.Node, LocalFunctionReachability>()
  const nestedFunctions = (body: ts.Node): LocalFunctionReachability => {
    const cached = localFunctionCache.get(body)
    if (cached !== undefined) return cached
    const result = inspectLocalFunctions(body)
    localFunctionCache.set(body, result)
    return result
  }
  const receiverReferences = (body: ts.Node, isStatic: boolean): Set<string> => {
    const names = new Set<string>()
    const locals = nestedFunctions(body)
    if (locals.reason !== null) invalidReason = locals.reason
    const visit = (node: ts.Node): void => {
      if (ts.isClassExpression(node) || ts.isClassDeclaration(node)) {
        invalidReason = 'Nested classes cannot be attributed to the selected class'
        return
      }
      if (ts.isFunctionDeclaration(node)) {
        if (locals.reachable.has(node) && node.body !== undefined) visit(node.body)
        return
      }
      if (ts.isFunctionExpression(node) || ts.isArrowFunction(node)) {
        invalidReason = 'Nested closures require a checked callable reachability graph'
        return
      }
      if (ts.isElementAccessExpression(node) && node.expression.kind === ts.SyntaxKind.ThisKeyword) {
        invalidReason = 'Computed receiver access cannot establish a frozen private-member reachability graph'
      }
      if (node.kind === ts.SyntaxKind.ThisKeyword) {
        const directMemberReceiver = (ts.isPropertyAccessExpression(node.parent)
          || ts.isElementAccessExpression(node.parent)) && node.parent.expression === node
        const directFluentReturn = ts.isReturnStatement(node.parent) && node.parent.expression === node
        if (!directMemberReceiver && !directFluentReturn) invalidReason = 'Aliased or escaping receivers require a checked private-member reachability graph'
      }
      const written = assignmentTarget(node)
      if (written !== null && ts.isPropertyAccessExpression(written)) {
        const receiver = written.expression
        const receiverKey = receiver.kind === ts.SyntaxKind.ThisKeyword ? memberKey(written.name.text, isStatic)
          : ts.isIdentifier(receiver) && receiver.text === className ? memberKey(written.name.text, true) : null
        if (receiverKey !== null && callableMemberKeys.has(receiverKey)) {
          invalidReason = 'Mutable callable member identity or captures cannot be represented by a complete state snapshot'
        }
        if (hasPrototypeAccess(written) || written.name.text === 'constructor') {
          invalidReason = 'Prototype and constructor mutations are outside the checked class lifecycle subset'
        }
      }
      if (ts.isDeleteExpression(node)) invalidReason = 'Deleted properties are outside the complete fixed-state snapshot subset'
      if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)
        && ts.isIdentifier(node.expression.expression) && ['Object', 'Reflect'].includes(node.expression.expression.text)
        && ['setPrototypeOf', 'defineProperty', 'defineProperties', 'set', 'deleteProperty'].includes(node.expression.name.text)) {
        invalidReason = 'Dynamic property or prototype mutation is outside the checked class lifecycle subset'
      }
      if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)
        && ts.isIdentifier(node.expression.expression) && node.expression.expression.text === 'Object'
        && node.expression.name.text === 'assign' && node.arguments[0] !== undefined
        && (hasPrototypeAccess(node.arguments[0])
          || (ts.isIdentifier(node.arguments[0]) && node.arguments[0].text === className))) {
        invalidReason = 'Dynamic property or prototype mutation is outside the checked class lifecycle subset'
      }
      if (hasPrivateCallableBodies && ts.isCallExpression(node) && ts.isIdentifier(node.expression)) {
        const name = node.expression.text
        const checkedLocal = [...locals.reachable].some((declaration) => declaration.name?.text === name)
        const nativeGlobal = nativeGlobals.has(name) && !shadowedNativeGlobals.has(name)
        if (!checkedLocal && !nativeGlobal) {
          invalidReason = 'Unchecked free helper calls cannot establish the selected private callable statement universe'
        }
      }
      if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'eval') {
        invalidReason = 'Dynamic evaluation cannot establish an original AST statement universe'
      }
      if (ts.isPropertyAccessExpression(node) && node.expression.kind === ts.SyntaxKind.ThisKeyword) {
        names.add(memberKey(node.name.text, isStatic))
      }
      if ((ts.isParameter(node) || ts.isVariableDeclaration(node) || ts.isBindingElement(node))
        && ts.isIdentifier(node.name) && node.name.text === className) {
        invalidReason = 'A shadowed class binding would make static private-member reachability ambiguous'
      }
      if (ts.isIdentifier(node) && node.text === className) {
        const parent = node.parent
        const typeReference = ts.isTypeReferenceNode(parent) && parent.typeName === node
        const constructorCall = ts.isNewExpression(parent) && parent.expression === node
        const directClassMember = ts.isPropertyAccessExpression(parent) && parent.expression === node
        const memberNameOnly = (ts.isPropertyAccessExpression(parent) && parent.name === node)
          || (ts.isMethodDeclaration(parent) && parent.name === node)
          || (ts.isPropertyAssignment(parent) && parent.name === node)
        if (!typeReference && !constructorCall && !directClassMember && !memberNameOnly) {
          invalidReason = 'Aliased, escaping or computed class receivers require a checked static private-member reachability graph'
        }
      }
      if (ts.isPropertyAccessExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === className) {
        names.add(memberKey(node.name.text, true))
      }
      if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.name.text === 'globalThis') {
        invalidReason = 'A local globalThis binding would shadow native statement counter storage'
      }
      ts.forEachChild(node, visit)
    }
    visit(body)
    return names
  }
  for (const initializer of initializers) receiverReferences(initializer.body, initializer.isStatic).forEach((name) => reachable.add(name))
  let changed = true
  while (changed) {
    changed = false
    for (const member of members) {
      if (!reachable.has(memberKey(member.name, member.isStatic))) continue
      for (const name of receiverReferences(member.body, member.isStatic)) {
        if (!reachable.has(name)) { reachable.add(name); changed = true }
      }
    }
  }
  if (invalidReason !== null) return unsupported(invalidReason)
  if (hasGlobalThisBinding(file)) return unsupported('A globalThis binding would shadow native statement counter storage')

  const inventoried = inspectClassSurface(selected, file)
  if (typeof inventoried === 'string') return unsupported(inventoried)

  const statements: ClassStatementDescriptor[] = []
  const insertions: Insertion[] = []
  const marker = (id: string): string => `(globalThis as unknown as Record<string, Set<string>>)[${JSON.stringify(counterGlobalName)}]?.add(${JSON.stringify(id)});`
  const describe = (node: ts.Node, name: string, kind: string): ClassStatementDescriptor => {
    const startOffset = node.getStart(file)
    const endOffset = node.getEnd()
    const start = file.getLineAndCharacterOfPosition(startOffset)
    const end = file.getLineAndCharacterOfPosition(endOffset)
    const descriptor = { id: `${sourceSha256}:${startOffset}:${endOffset}:${kind}`, memberName: name, kind,
      startOffset, endOffset, startLine: start.line + 1, startColumn: start.character,
      endLine: end.line + 1, endColumn: end.character }
    statements.push(descriptor)
    return descriptor
  }
  const instrumentExpression = (node: ts.Node, name: string, kind: string): void => {
    const statement = describe(node, name, kind)
    const hit = marker(statement.id).slice(0, -1)
    insertions.push({ offset: node.getStart(file), text: `(${hit}, (`, order: 0 })
    insertions.push({ offset: node.getEnd(), text: '))', order: 1 })
  }
  const instrumentBody = (body: ts.Node, name: string): void => {
    const locals = nestedFunctions(body)
    const visit = (node: ts.Node): void => {
      if (ts.isFunctionDeclaration(node)) {
        if (locals.reachable.has(node) && node.body !== undefined) visit(node.body)
        return
      }
      if (ts.isStatement(node) && executableStatement(node)) {
        const statement = describe(node, name, ts.SyntaxKind[node.kind])
        let anchor = node
        while (ts.isLabeledStatement(anchor.parent)) anchor = anchor.parent
        const parent = anchor.parent
        const inList = ts.isBlock(parent) || ts.isCaseClause(parent) || ts.isDefaultClause(parent)
        const prefix = `${marker(statement.id)} `
        if (inList) {
          // A parenthesized counter must not continue a preceding semicolonless expression.
          insertions.push({ offset: anchor.getStart(file), text: `; ${prefix}`, order: 0 })
        } else {
          insertions.push({ offset: anchor.getStart(file), text: `{ ${prefix}`, order: 0 })
          insertions.push({ offset: anchor.getEnd(), text: ' }', order: 1 })
        }
      }
      ts.forEachChild(node, visit)
    }
    visit(body)
  }
  for (const initializer of initializers) instrumentExpression(initializer.body, initializer.name, 'PropertyInitializer')
  for (const member of members) {
    if (!reachable.has(memberKey(member.name, member.isStatic))) continue
    if (ts.isBlock(member.body)) instrumentBody(member.body, member.name)
    else instrumentExpression(member.body, member.name, 'CallableExpressionBody')
  }
  statements.sort((left, right) => left.startOffset - right.startOffset || left.endOffset - right.endOffset)
  if (statements.length === 0) return unsupported('The selected class has no executable statements')
  // For coincident close/open positions, close the earlier statement before opening the next one.
  insertions.sort((left, right) => right.offset - left.offset || left.order - right.order)
  let source = file.text
  for (const insertion of insertions) source = source.slice(0, insertion.offset) + insertion.text + source.slice(insertion.offset)
  return { ...base, source, reason: null, instrumentedSha256: hash(source), statements, inventory: inventoried }
}

function memberName(member: ts.ClassElement): string | null {
  const name = member.name
  if (name !== undefined && (ts.isIdentifier(name) || ts.isPrivateIdentifier(name) || ts.isStringLiteral(name))) return name.text
  return null
}

function executableStatement(node: ts.Statement): boolean {
  if (ts.isBlock(node) || ts.isEmptyStatement(node) || ts.isLabeledStatement(node)
    || ts.isFunctionDeclaration(node) || ts.isClassDeclaration(node) || ts.isInterfaceDeclaration(node)
    || ts.isTypeAliasDeclaration(node)) return false
  if (ts.isVariableStatement(node) && node.declarationList.declarations.every((declaration) => declaration.initializer === undefined)) return false
  const directive = ts.isExpressionStatement(node) && ts.isStringLiteral(node.expression)
  return !directive
}

function hasGlobalThisBinding(file: ts.SourceFile): boolean {
  let found = false
  const visit = (node: ts.Node): void => {
    if (ts.isIdentifier(node) && node.text === 'globalThis') {
      const parent = node.parent
      const declaration = (ts.isParameter(parent) || ts.isVariableDeclaration(parent) || ts.isBindingElement(parent)
        || ts.isImportSpecifier(parent) || ts.isImportClause(parent) || ts.isNamespaceImport(parent)
        || ts.isFunctionDeclaration(parent) || ts.isClassDeclaration(parent)) && parent.name === node
      if (declaration) found = true
    }
    ts.forEachChild(node, visit)
  }
  visit(file)
  return found
}

function hash(value: string): string { return createHash('sha256').update(value, 'utf8').digest('hex') }


interface LocalFunctionReachability { reachable: Set<ts.FunctionDeclaration>; reason: string | null }

/** Checked local subset: uniquely named declarations in the member body, used only by direct calls. */
function inspectLocalFunctions(body: ts.Node): LocalFunctionReachability {
  const reachable = new Set<ts.FunctionDeclaration>()
  let reason: string | null = null
  const declarations = ts.isBlock(body) ? body.statements.filter(ts.isFunctionDeclaration) : []
  const byName = new Map<string, ts.FunctionDeclaration>()
  for (const declaration of declarations) {
    if (declaration.name === undefined || declaration.body === undefined) {
      reason = 'Local callable declarations must have an exact name and body'
      continue
    }
    const asynchronous = declaration.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.AsyncKeyword)
      || declaration.asteriskToken !== undefined
    if (asynchronous || byName.has(declaration.name.text)) {
      reason = 'Async, generator or overloaded local callables are outside the checked reachability subset'
      continue
    }
    byName.set(declaration.name.text, declaration)
  }
  const scan = (node: ts.Node, localOwner: ts.FunctionDeclaration | null): void => {
    if (ts.isFunctionDeclaration(node)) {
      if (!declarations.includes(node)) reason = 'Nested local function scopes require a checked callable reachability graph'
      return
    }
    if (ts.isFunctionExpression(node) || ts.isArrowFunction(node)) {
      reason = 'Nested closures require a checked callable reachability graph'
      return
    }
    if (localOwner !== null && node.kind === ts.SyntaxKind.ThisKeyword) {
      reason = 'Direct-called local functions cannot use a class receiver implicitly'
    }
    if (ts.isIdentifier(node) && byName.has(node.text)) {
      const parent = node.parent
      const directCall = ts.isCallExpression(parent) && parent.expression === node
      const declarationName = ts.isFunctionDeclaration(parent) && parent.name === node
      if (directCall) reachable.add(byName.get(node.text)!)
      else if (!declarationName) reason = 'Aliased, escaped or shadowed local callables require a checked reachability graph'
    }
    ts.forEachChild(node, (child) => scan(child, localOwner))
  }
  scan(body, null)
  const scanned = new Set<ts.FunctionDeclaration>()
  while (scanned.size < reachable.size) {
    for (const declaration of reachable) {
      if (scanned.has(declaration)) continue
      scanned.add(declaration)
      if (declaration.body !== undefined) scan(declaration.body, declaration)
    }
  }
  return { reachable, reason }
}


function inspectClassSurface(selected: ts.ClassDeclaration, file: ts.SourceFile): ClassSurfaceInventory | string {
  const constructorParameterNames: string[] = []
  const publicMembers: ClassCallableDescriptor[] = []
  const stateFields = new Map<string, ClassStateFieldDescriptor>()
  const callableOrAccessorNames = new Set<string>()
  let reason: string | null = null
  const parameterNames = (parameters: ts.NodeArray<ts.ParameterDeclaration>): string[] => parameters.map((parameter) => {
    if (!ts.isIdentifier(parameter.name) || parameter.dotDotDotToken !== undefined) {
      reason = 'Destructured and rest class parameters are outside the frozen harness subset'
      return '<unsupported>'
    }
    return parameter.name.text
  })
  for (const member of selected.members) {
    const modifiers = ts.canHaveModifiers(member) ? ts.getModifiers(member) ?? [] : []
    const isStatic = modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.StaticKeyword)
    if (ts.isConstructorDeclaration(member)) {
      if (member.body === undefined) continue
      constructorParameterNames.push(...parameterNames(member.parameters))
      for (const parameter of member.parameters) {
        const parameterProperty = parameter.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.PublicKeyword
          || modifier.kind === ts.SyntaxKind.PrivateKeyword || modifier.kind === ts.SyntaxKind.ProtectedKeyword
          || modifier.kind === ts.SyntaxKind.ReadonlyKeyword)
        if (parameterProperty && ts.isIdentifier(parameter.name)) {
          stateFields.set(parameter.name.text, { name: parameter.name.text, isEcmaPrivate: false,
            typeText: parameter.type?.getText(file) ?? null })
        }
      }
      continue
    }
    const name = memberName(member)
    if (name === null) continue
    const isPrivate = name.startsWith('#') || modifiers.some((modifier) =>
      modifier.kind === ts.SyntaxKind.PrivateKeyword || modifier.kind === ts.SyntaxKind.ProtectedKeyword)
    const callable = ts.isPropertyDeclaration(member)
      ? (member.initializer !== undefined && (ts.isArrowFunction(member.initializer) || ts.isFunctionExpression(member.initializer))
        ? member.initializer : member.type !== undefined && ts.isFunctionTypeNode(member.type) ? member.type : null)
      : ts.isMethodDeclaration(member) || ts.isGetAccessorDeclaration(member) || ts.isSetAccessorDeclaration(member) ? member : null
    if (callable !== null) {
      if (!isStatic) callableOrAccessorNames.add(name)
      const bodylessMethod = ts.isMethodDeclaration(callable) && callable.body === undefined
      if (isPrivate || bodylessMethod) continue
      const kind = ts.isGetAccessorDeclaration(member) ? 'GETTER' : ts.isSetAccessorDeclaration(member) ? 'SETTER'
        : ts.isPropertyDeclaration(member) ? 'FUNCTION_FIELD' : 'METHOD'
      publicMembers.push({ name, kind, isStatic, parameterNames: parameterNames(callable.parameters),
        parameterTypeTexts: callable.parameters.map((parameter) => parameter.type?.getText(file) ?? null),
        resultTypeText: callable.type?.getText(file) ?? null })
    } else if (ts.isPropertyDeclaration(member) && !isStatic) {
      stateFields.set(name, { name, isEcmaPrivate: name.startsWith('#'), typeText: member.type?.getText(file) ?? null })
    }
  }
  const visitWrites = (node: ts.Node): void => {
    if (node !== selected && (ts.isClassDeclaration(node) || ts.isClassExpression(node))) return
    let written: ts.Node | undefined
    if (ts.isBinaryExpression(node) && node.operatorToken.kind >= ts.SyntaxKind.FirstAssignment
      && node.operatorToken.kind <= ts.SyntaxKind.LastAssignment) written = node.left
    if (ts.isPrefixUnaryExpression(node) || ts.isPostfixUnaryExpression(node)) {
      if (node.operator === ts.SyntaxKind.PlusPlusToken || node.operator === ts.SyntaxKind.MinusMinusToken) written = node.operand
    }
    if (written !== undefined && ts.isPropertyAccessExpression(written) && written.expression.kind === ts.SyntaxKind.ThisKeyword) {
      const name = written.name.text
      if (!callableOrAccessorNames.has(name) && !stateFields.has(name)) {
        stateFields.set(name, { name, isEcmaPrivate: name.startsWith('#'), typeText: null })
      }
    }
    ts.forEachChild(node, visitWrites)
  }
  for (const member of selected.members) {
    const isStatic = ts.canHaveModifiers(member) && ts.getModifiers(member)?.some((modifier) => modifier.kind === ts.SyntaxKind.StaticKeyword)
    if (!isStatic) visitWrites(member)
  }
  if (reason !== null) return reason
  return { constructorParameterNames, publicMembers,
    instanceDataFields: [...stateFields.values()].sort((left, right) => left.name.localeCompare(right.name)),
    dataClasses: inspectDataClasses(file) }
}


function assignmentTarget(node: ts.Node): ts.Node | null {
  if (ts.isBinaryExpression(node) && node.operatorToken.kind >= ts.SyntaxKind.FirstAssignment
    && node.operatorToken.kind <= ts.SyntaxKind.LastAssignment) return node.left
  if ((ts.isPrefixUnaryExpression(node) || ts.isPostfixUnaryExpression(node))
    && (node.operator === ts.SyntaxKind.PlusPlusToken || node.operator === ts.SyntaxKind.MinusMinusToken)) return node.operand
  return null
}

function hasPrototypeAccess(node: ts.Node): boolean {
  let found = false
  const visit = (part: ts.Node): void => {
    if (ts.isPropertyAccessExpression(part) && ['prototype', '__proto__'].includes(part.name.text)) found = true
    if (ts.isElementAccessExpression(part) && ts.isStringLiteral(part.argumentExpression)
      && ['prototype', '__proto__'].includes(part.argumentExpression.text)) found = true
    ts.forEachChild(part, visit)
  }
  visit(node)
  return found
}


function declaredValueBindings(file: ts.SourceFile): Set<string> {
  const names = new Set<string>()
  const visit = (node: ts.Node): void => {
    if (ts.isIdentifier(node)) {
      const parent = node.parent
      const valueBinding = (ts.isParameter(parent) || ts.isVariableDeclaration(parent) || ts.isBindingElement(parent)
        || ts.isImportSpecifier(parent) || ts.isImportClause(parent) || ts.isNamespaceImport(parent)
        || ts.isFunctionDeclaration(parent) || ts.isClassDeclaration(parent)) && parent.name === node
      if (valueBinding) names.add(node.text)
    }
    ts.forEachChild(node, visit)
  }
  visit(file)
  return names
}


/** Source identity is the same captured module; registries cannot admit arbitrary instance prototypes. */
function inspectDataClasses(file: ts.SourceFile): ClassDataClassDescriptor[] {
  const candidates = file.statements.filter(ts.isClassDeclaration)
  const typeAliases = new Map(file.statements.filter(ts.isTypeAliasDeclaration).map((node) => [node.name.text, node.type]))
  const valueNames = declaredValueBindings(file)
  const safeName = (name: string): boolean => /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)
    && !['__proto__', 'prototype', 'constructor', '__usvmCallsClassIdentity'].includes(name)
  const callableType = (type: ts.TypeNode | undefined, parameters: Map<string, ts.TypeNode | undefined>, seen = new Set<string>()): boolean => {
    if (type === undefined) return false
    if (ts.isFunctionTypeNode(type) || ts.isConstructorTypeNode(type) || ts.isTypeQueryNode(type)) return true
    if (ts.isTypeReferenceNode(type) && ts.isIdentifier(type.typeName)) {
      const name = type.typeName.text
      if (name === 'Function' || seen.has(name)) return true
      const referenced = typeAliases.get(name) ?? parameters.get(name)
      if (referenced !== undefined) {
        const nestedSeen = new Set(seen)
        nestedSeen.add(name)
        if (callableType(referenced, parameters, nestedSeen)) return true
      }
    }
    let found = false
    ts.forEachChild(type, (node) => {
      if (ts.isTypeNode(node) && callableType(node, parameters, seen)) found = true
      if (ts.isCallSignatureDeclaration(node) || ts.isConstructSignatureDeclaration(node)) found = true
    })
    return found
  }
  const pureValue = (node: ts.Expression, inputs: Set<string>): boolean => {
    if (ts.isParenthesizedExpression(node) || ts.isAsExpression(node) || ts.isTypeAssertionExpression(node)
      || ts.isSatisfiesExpression(node) || ts.isNonNullExpression(node)) return pureValue(node.expression, inputs)
    if (ts.isStringLiteral(node) || ts.isNumericLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)
      || node.kind === ts.SyntaxKind.TrueKeyword || node.kind === ts.SyntaxKind.FalseKeyword
      || node.kind === ts.SyntaxKind.NullKeyword) return true
    if (ts.isIdentifier(node)) return inputs.has(node.text)
      || (['undefined', 'NaN', 'Infinity'].includes(node.text) && !valueNames.has(node.text))
    if (ts.isPrefixUnaryExpression(node)
      && [ts.SyntaxKind.PlusToken, ts.SyntaxKind.MinusToken, ts.SyntaxKind.ExclamationToken, ts.SyntaxKind.TildeToken].includes(node.operator)) {
      // Coercing an unknown constructor input can invoke valueOf or Symbol.toPrimitive.
      return pureValue(node.operand, new Set())
    }
    if (ts.isArrayLiteralExpression(node)) return node.elements.every((element) =>
      !ts.isSpreadElement(element) && !ts.isOmittedExpression(element) && pureValue(element, inputs))
    if (ts.isObjectLiteralExpression(node)) return node.properties.every((property) => {
      if (!ts.isPropertyAssignment(property)) return false
      const name = property.name
      const literalName = ts.isIdentifier(name) || ts.isStringLiteral(name)
      return literalName && safeName(name.text) && pureValue(property.initializer, inputs)
    })
    return false
  }
  const result: ClassDataClassDescriptor[] = []
  for (const candidate of candidates) {
    const modifiers = ts.getModifiers(candidate) ?? []
    const exported = modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword)
    const disallowedClassModifier = modifiers.some((modifier) => [ts.SyntaxKind.DefaultKeyword,
      ts.SyntaxKind.AbstractKeyword, ts.SyntaxKind.DeclareKeyword].includes(modifier.kind))
    const exportName = candidate.name?.text
    if (!exported || disallowedClassModifier || exportName === undefined || !safeName(exportName)) continue
    const decorated = (ts.getDecorators(candidate)?.length ?? 0) > 0
    if (decorated || (candidate.heritageClauses?.length ?? 0) > 0 || !uniqueDirectClassExport(file, candidate, exportName)) continue
    const parameters = new Map((candidate.typeParameters ?? []).map((parameter) => [parameter.name.text, parameter.constraint]))
    const fields = new Set<string>()
    let eligible = true
    let constructor: ts.ConstructorDeclaration | undefined
    for (const member of candidate.members) {
      if (ts.isSemicolonClassElement(member)) continue
      const memberModifiers = ts.canHaveModifiers(member) ? ts.getModifiers(member) ?? [] : []
      const memberDecorated = ts.canHaveDecorators(member) && (ts.getDecorators(member)?.length ?? 0) > 0
      const isStatic = memberModifiers.some((modifier) => modifier.kind === ts.SyntaxKind.StaticKeyword)
      if (memberDecorated || isStatic) { eligible = false; break }
      if (ts.isConstructorDeclaration(member)) {
        if (member.body === undefined) continue
        if (constructor !== undefined) { eligible = false; break }
        constructor = member
        continue
      }
      if (!ts.isPropertyDeclaration(member) || member.name === undefined || ts.isPrivateIdentifier(member.name)
        || (!ts.isIdentifier(member.name) && !ts.isStringLiteral(member.name))) { eligible = false; break }
      const name = member.name.text
      const valueInitializer = member.initializer
      const safeInitializer = valueInitializer === undefined || pureValue(valueInitializer, new Set())
      if (!safeName(name) || fields.has(name) || callableType(member.type, parameters) || !safeInitializer) {
        eligible = false
        break
      }
      fields.add(name)
    }
    if (!eligible) continue
    if (constructor !== undefined) {
      const inputs = new Set<string>()
      for (const parameter of constructor.parameters) {
        if (!ts.isIdentifier(parameter.name) || parameter.dotDotDotToken !== undefined
          || inputs.has(parameter.name.text) || callableType(parameter.type, parameters)) { eligible = false; break }
        inputs.add(parameter.name.text)
        if (parameter.initializer !== undefined && !pureValue(parameter.initializer, inputs)) { eligible = false; break }
        const parameterProperty = parameter.modifiers?.some((modifier) => [ts.SyntaxKind.PublicKeyword,
          ts.SyntaxKind.PrivateKeyword, ts.SyntaxKind.ProtectedKeyword, ts.SyntaxKind.ReadonlyKeyword].includes(modifier.kind))
        if (parameterProperty) {
          const name = parameter.name.text
          if (!safeName(name) || fields.has(name)) { eligible = false; break }
          fields.add(name)
        }
      }
      if (!eligible) continue
      for (const statement of constructor.body!.statements) {
        if (ts.isEmptyStatement(statement)) continue
        if (!ts.isExpressionStatement(statement) || !ts.isBinaryExpression(statement.expression)
          || statement.expression.operatorToken.kind !== ts.SyntaxKind.EqualsToken) { eligible = false; break }
        const assignment = statement.expression
        const target = assignment.left
        const ownField = ts.isPropertyAccessExpression(target) && target.expression.kind === ts.SyntaxKind.ThisKeyword
          && fields.has(target.name.text)
        if (!ownField || !pureValue(assignment.right, inputs)) { eligible = false; break }
      }
    }
    if (!eligible) continue
    // An exported class binding must retain the source-declared constructor identity.
    const modified = classBindingModified(file, exportName)
    if (modified) continue
    result.push({ exportName, fields: [...fields].sort() })
  }
  return result.sort((left, right) => left.exportName.localeCompare(right.exportName))
}

export function uniqueDirectClassExport(file: ts.SourceFile, candidate: ts.ClassDeclaration, name: string): boolean {
  const bindsName = (binding: ts.BindingName): boolean => ts.isIdentifier(binding) ? binding.text === name
    : binding.elements.some((element) => !ts.isOmittedExpression(element) && bindsName(element.name))
  let declarations = 0
  for (const statement of file.statements) {
    if ((ts.isClassDeclaration(statement) || ts.isFunctionDeclaration(statement) || ts.isEnumDeclaration(statement)
      || ts.isModuleDeclaration(statement)) && statement.name?.getText(file) === name) declarations++
    if (ts.isVariableStatement(statement) && statement.declarationList.declarations.some((declaration) =>
      bindsName(declaration.name))) declarations++
    if (ts.isImportEqualsDeclaration(statement) && !statement.isTypeOnly && statement.name.text === name) declarations++
    if (ts.isImportDeclaration(statement) && statement.importClause !== undefined && !statement.importClause.isTypeOnly) {
      const clause = statement.importClause
      const bindings = clause.namedBindings
      const defaultBinding = clause.name?.text === name
      const namespaceBinding = bindings !== undefined && ts.isNamespaceImport(bindings) && bindings.name.text === name
      const namedBinding = bindings !== undefined && ts.isNamedImports(bindings)
        && bindings.elements.some((element) => !element.isTypeOnly && element.name.text === name)
      if (defaultBinding || namespaceBinding || namedBinding) declarations++
    }
    const conflictingExport = ts.isExportDeclaration(statement) && !statement.isTypeOnly
      && statement.exportClause !== undefined && ts.isNamedExports(statement.exportClause)
      && statement.exportClause.elements.some((element) => !element.isTypeOnly && element.name.text === name)
    if (conflictingExport) return false
  }
  return declarations === 1 && candidate.name?.text === name
}

export function classBindingModified(file: ts.SourceFile, name: string): boolean {
  const writesName = (target: ts.Node): boolean => {
    if (ts.isIdentifier(target)) return target.text === name
    if (ts.isParenthesizedExpression(target)) return writesName(target.expression)
    if (ts.isArrayLiteralExpression(target)) return target.elements.some(writesName)
    if (ts.isObjectLiteralExpression(target)) return target.properties.some((property) => {
      if (ts.isShorthandPropertyAssignment(property)) return property.name.text === name
      if (ts.isPropertyAssignment(property)) return writesName(property.initializer)
      return ts.isSpreadAssignment(property) && writesName(property.expression)
    })
    if (ts.isSpreadElement(target)) return writesName(target.expression)
    if (ts.isBinaryExpression(target) && target.operatorToken.kind === ts.SyntaxKind.EqualsToken) return writesName(target.left)
    return false
  }
  let modified = false
  const visit = (node: ts.Node): void => {
    const target = assignmentTarget(node)
    if (target !== null && writesName(target)) modified = true
    ts.forEachChild(node, visit)
  }
  visit(file)
  return modified
}
