import { existsSync, readFileSync, realpathSync, statSync } from 'node:fs'
import path from 'node:path'
import ts from 'typescript'

interface LocalSourceClosure {
  files: string[]
  reasonCode?: string
  diagnostic?: string
}

/** Inspect source only. Never execute a project or consult a profile/model result. */
export function inspectLocalSourceClosure(rootPath: string, entryPath: string): LocalSourceClosure {
  const root = realpathSync(rootPath)
  const pending = [path.resolve(root, path.relative(path.resolve(rootPath), path.resolve(entryPath)))]
  const files = new Set<string>()
  const runtimeImportRanges = new Map<string, Array<{ start: number, end: number }>>()
  const sources = new Map<string, ts.SourceFile>()
  const dependencies = new Map<string, string[]>()

  const reject = (reasonCode: string, diagnostic: string): LocalSourceClosure => ({
    files: [...files].sort(), reasonCode, diagnostic,
  })

  while (pending.length > 0) {
    const file = pending.shift()!
    const relative = path.relative(root, file)
    if (relative.startsWith(`..${path.sep}`) || relative === '..' || path.isAbsolute(relative)
      || realpathSync(file) !== file) {
      return reject('IMPORTED_CALLEES_UNSUPPORTED', `Source escapes the project or uses a symlink: ${file}`)
    }
    if (files.has(relative)) continue
    files.add(relative)

    const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true)
    sources.set(file, source)
    const edges: string[] = []
    dependencies.set(file, edges)
    const imports: string[] = []
    const ranges: Array<{ start: number, end: number }> = []
    runtimeImportRanges.set(file, ranges)
    for (const statement of source.statements) {
      if (ts.isImportDeclaration(statement)) {
        const clause = statement.importClause
        if (clause?.isTypeOnly) continue
        if (clause && !clause.name && clause.namedBindings && ts.isNamedImports(clause.namedBindings)
          && clause.namedBindings.elements.length > 0
          && clause.namedBindings.elements.every(element => element.isTypeOnly)) continue
        if (!ts.isStringLiteral(statement.moduleSpecifier)) {
          return reject('IMPORTED_CALLEES_UNSUPPORTED', `Nonliteral module specifier in ${relative}`)
        }
        imports.push(statement.moduleSpecifier.text)
        ranges.push({ start: statement.getStart(source), end: statement.end })
      } else if (ts.isExportDeclaration(statement)) {
        if (statement.isTypeOnly) continue
        if (statement.exportClause && ts.isNamedExports(statement.exportClause)
          && statement.exportClause.elements.length > 0
          && statement.exportClause.elements.every(element => element.isTypeOnly)) continue
        ranges.push({ start: statement.getStart(source), end: statement.end })
        if (statement.moduleSpecifier) {
          if (!ts.isStringLiteral(statement.moduleSpecifier)) {
            return reject('IMPORTED_CALLEES_UNSUPPORTED', `Nonliteral re-export in ${relative}`)
          }
          imports.push(statement.moduleSpecifier.text)
        }
      } else if (ts.isImportEqualsDeclaration(statement)) {
        if (!statement.isTypeOnly) {
          return reject('IMPORTED_CALLEES_UNSUPPORTED', `Import assignment in ${relative}`)
        }
      } else if (!safeModuleStatement(statement, source)) {
        return reject('TOP_LEVEL_INITIALIZATION_UNSUPPORTED',
          `Unsupported module initialization in ${relative} at offset ${statement.getStart(source)}`)
      }
    }

    let dynamicImport = false
    const visit = (node: ts.Node): void => {
      if (ts.isCallExpression(node) && (node.expression.kind === ts.SyntaxKind.ImportKeyword
        || ts.isIdentifier(node.expression) && node.expression.text === 'require')) dynamicImport = true
      ts.forEachChild(node, visit)
    }
    visit(source)
    if (dynamicImport) return reject('IMPORTED_CALLEES_UNSUPPORTED', `Dynamic import or require in ${relative}`)

    for (const specifier of imports) {
      if (!specifier.startsWith('./') && !specifier.startsWith('../')) {
        return reject('IMPORTED_CALLEES_UNSUPPORTED', `External or aliased module ${specifier} in ${relative}`)
      }
      const target = path.resolve(path.dirname(file), specifier)
      if (existsSync(path.join(target, 'package.json'))) {
        return reject('IMPORTED_CALLEES_UNSUPPORTED', `Directory package resolution is unsupported: ${specifier} in ${relative}`)
      }
      if (target.endsWith('.js') && existsSync(target)) {
        return reject('IMPORTED_CALLEES_UNSUPPORTED', `JavaScript runtime source is unsupported: ${specifier} in ${relative}`)
      }
      const candidates = (target.endsWith('.js')
        ? [target.slice(0, -3) + '.ts']
        : [target, `${target}.ts`, path.join(target, 'index.ts')])
        .filter(candidate => candidate.endsWith('.ts') && !candidate.endsWith('.d.ts'))
        .filter(candidate => existsSync(candidate) && statSync(candidate).isFile())
      if (candidates.length !== 1) {
        return reject('IMPORTED_CALLEES_UNSUPPORTED',
          `Expected one local TypeScript module for ${specifier} in ${relative}, found ${candidates.length}`)
      }
      pending.push(candidates[0]!)
      edges.push(candidates[0]!)
    }
  }

  // Replay overlays link dependency files to the original checkout. A back-import
  // would then create a second entry-module instance with different mutable state.
  if (hasDependencyCycle(dependencies)) {
    return reject('IMPORTED_CALLEES_UNSUPPORTED', 'Cyclic local module dependencies are unsupported by source replay')
  }

  // Ask TypeScript to validate the imported bindings as well as the files. Diagnostics
  // in unrelated function bodies do not determine closure admission.
  if ([...runtimeImportRanges.values()].some(ranges => ranges.length > 0)) {
    const options: ts.CompilerOptions = {
      target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext,
      moduleResolution: ts.ModuleResolutionKind.Node10, noLib: true, noEmit: true,
      allowImportingTsExtensions: true, types: [],
    }
    const host = ts.createCompilerHost(options)
    const directories = new Set<string>()
    for (const file of sources.keys()) {
      let directory = path.dirname(file)
      while (directory.startsWith(root)) {
        directories.add(directory)
        if (directory === root) break
        directory = path.dirname(directory)
      }
    }
    host.fileExists = file => sources.has(path.resolve(file))
    host.readFile = file => sources.get(path.resolve(file))?.text
    host.getSourceFile = file => sources.get(path.resolve(file))
    host.directoryExists = directory => directories.has(path.resolve(directory))
    host.getDirectories = () => []
    host.realpath = file => path.resolve(file)
    const program = ts.createProgram([...sources.keys()], options, host)
    for (const [file, ranges] of runtimeImportRanges) {
      if (ranges.length === 0) continue
      const source = program.getSourceFile(file)!
      const invalidImport = program.getSemanticDiagnostics(source).find(diagnostic =>
        diagnostic.category === ts.DiagnosticCategory.Error && diagnostic.start !== undefined
        && ranges.some(range => diagnostic.start! >= range.start && diagnostic.start! < range.end))
      if (invalidImport) {
        return reject('IMPORTED_CALLEES_UNSUPPORTED',
          `${path.relative(root, file)}: ${ts.flattenDiagnosticMessageText(invalidImport.messageText, '\n')}`)
      }
    }
  }

  return { files: [...files].sort() }
}

function hasDependencyCycle(dependencies: Map<string, string[]>): boolean {
  const active = new Set<string>()
  const visited = new Set<string>()
  const visit = (file: string): boolean => {
    if (active.has(file)) return true
    if (visited.has(file)) return false
    active.add(file)
    for (const dependency of dependencies.get(file) ?? []) {
      if (visit(dependency)) return true
    }
    active.delete(file)
    visited.add(file)
    return false
  }
  return [...dependencies.keys()].some(visit)
}

function safeModuleStatement(statement: ts.Statement, source: ts.SourceFile): boolean {
  if (ts.isFunctionDeclaration(statement) || ts.isInterfaceDeclaration(statement)
    || ts.isTypeAliasDeclaration(statement) || ts.isEmptyStatement(statement)) return true
  if (ts.isExpressionStatement(statement) && ts.isStringLiteral(statement.expression)) return true
  if (ts.isVariableStatement(statement)) {
    return statement.declarationList.declarations.every(declaration => ts.isIdentifier(declaration.name)
      && (!declaration.initializer || safeInitializer(declaration.initializer, source)))
  }
  if (ts.isExportAssignment(statement)) return !statement.isExportEquals && safeInitializer(statement.expression, source)
  if (ts.isClassDeclaration(statement)) {
    if (statement.heritageClauses?.length || ts.getDecorators(statement)?.length) return false
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

function safeInitializer(expression: ts.Expression, source: ts.SourceFile, seen = new Set<ts.Node>()): boolean {
  if (seen.has(expression)) return false
  seen.add(expression)
  if (ts.isParenthesizedExpression(expression) || ts.isAsExpression(expression)
    || ts.isTypeAssertionExpression(expression) || ts.isSatisfiesExpression(expression)
    || ts.isNonNullExpression(expression)) return safeInitializer(expression.expression, source, seen)
  if (ts.isLiteralExpression(expression) || ts.isArrowFunction(expression) || ts.isFunctionExpression(expression)
    || expression.kind === ts.SyntaxKind.TrueKeyword || expression.kind === ts.SyntaxKind.FalseKeyword
    || expression.kind === ts.SyntaxKind.NullKeyword) return true
  if (ts.isPrefixUnaryExpression(expression)
    && [ts.SyntaxKind.PlusToken, ts.SyntaxKind.MinusToken, ts.SyntaxKind.ExclamationToken, ts.SyntaxKind.TildeToken]
      .includes(expression.operator)) return safeInitializer(expression.operand, source, seen)
  if (!ts.isIdentifier(expression)) return false
  for (const statement of source.statements) {
    if (ts.isFunctionDeclaration(statement) && statement.name?.text === expression.text) return Boolean(statement.body)
    if (!ts.isVariableStatement(statement)) continue
    for (const declaration of statement.declarationList.declarations) {
      if (ts.isIdentifier(declaration.name) && declaration.name.text === expression.text && declaration.initializer
        && declaration.getStart(source) < expression.getStart(source)
        && (ts.isArrowFunction(declaration.initializer) || ts.isFunctionExpression(declaration.initializer))) return true
    }
  }
  return false
}
