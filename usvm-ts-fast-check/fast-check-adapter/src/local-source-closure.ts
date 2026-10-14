import { readFileSync, realpathSync } from 'node:fs'
import path from 'node:path'
import ts from 'typescript'
import { FrozenModuleResolver, type FrozenModuleResolution } from './frozen-module-resolution.js'
import { unsupportedModuleInitialization } from './module-initialization.js'

interface LocalSourceClosure {
  files: string[]
  resolution?: FrozenModuleResolution
  reasonCode?: string
  diagnostic?: string
  initializationIssues?: Array<{ sourceFile: string, startOffset: number, diagnostic: string }>
}

/** Inspect source only. Never execute a project or consult a profile/model result. */
export function inspectLocalSourceClosure(rootPath: string, entryPath: string): LocalSourceClosure {
  return inspectSourceClosure(rootPath, entryPath, 'checked')
}

/** Graph acquisition only: initialization limitations remain explicit diagnostics for the symbolic startup driver. */
export function inspectStartupSourceClosure(rootPath: string, entryPath: string): LocalSourceClosure {
  return inspectSourceClosure(rootPath, entryPath, 'startup')
}

function inspectSourceClosure(rootPath: string, entryPath: string, mode: 'checked' | 'startup'): LocalSourceClosure {
  const root = realpathSync(rootPath)
  const entry = path.resolve(root, path.relative(path.resolve(rootPath), path.resolve(entryPath)))
  let resolver: FrozenModuleResolver
  try {
    resolver = new FrozenModuleResolver(root, entry)
  } catch (error) {
    return { files: [], reasonCode: 'IMPORTED_CALLEES_UNSUPPORTED', diagnostic: String(error) }
  }
  const pending = [entry]
  const initializationIssues: NonNullable<LocalSourceClosure['initializationIssues']> = []
  let entryAmbientIssue: string | undefined
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
    const emittedModules = runtimeModuleSpecifiers(source, resolver.options)
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
        if (!emittedModules.has(statement.moduleSpecifier.text)
          && importedBindingsUsedOnlyAsTypes(statement, source)) continue
        imports.push(statement.moduleSpecifier.text)
        ranges.push({ start: statement.getStart(source), end: statement.end })
      } else if (ts.isExportDeclaration(statement)) {
        if (statement.isTypeOnly) continue
        if (statement.exportClause && ts.isNamedExports(statement.exportClause)
          && statement.exportClause.elements.length > 0
          && statement.exportClause.elements.every(element => element.isTypeOnly)) continue
        if (statement.moduleSpecifier && ts.isStringLiteral(statement.moduleSpecifier)
          && !emittedModules.has(statement.moduleSpecifier.text)) continue
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
      } else {
        if (isAmbientRuntimeDeclaration(statement)) {
          const diagnostic = `Ambient runtime declaration in ${relative} at offset ${statement.getStart(source)}`
          if (mode === 'startup') initializationIssues.push({ sourceFile: relative, startOffset: statement.getStart(source), diagnostic })
          else {
            if (file !== entry) return reject('IMPORTED_CALLEES_UNSUPPORTED', diagnostic)
            entryAmbientIssue ??= diagnostic
          }
        }
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
      try {
        const target = resolver.resolve(specifier, file)
        pending.push(target)
        edges.push(target)
      } catch (error) {
        return reject('IMPORTED_CALLEES_UNSUPPORTED', String(error))
      }
    }
  }

  if (files.size > 1 && entryAmbientIssue !== undefined) {
    return reject('IMPORTED_CALLEES_UNSUPPORTED', entryAmbientIssue)
  }

  // Ask TypeScript to validate the imported bindings as well as the files. Diagnostics
  // in unrelated function bodies do not determine closure admission.
  if (mode === 'startup' || files.size > 1 || [...runtimeImportRanges.values()].some(ranges => ranges.length > 0)) {
    const options: ts.CompilerOptions = { ...resolver.options, noLib: true, noEmit: true, types: [] }
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
    host.resolveModuleNames = (names, containingFile) => names.map(name => resolver.resolvedModule(name, containingFile))
    const program = ts.createProgram([...sources.keys()], options, host)
    // A single source retains its dedicated capture validation. For a module
    // closure, initializer identities are attested by this exact source program.
    if (mode === 'startup' || files.size > 1) {
      const checker = program.getTypeChecker()
      const closure = new Set(sources.values())
      const evaluationOrder = moduleEvaluationOrder(dependencies, entry)
      for (const source of closure) {
        const unsupported = unsupportedModuleInitialization(source, checker, closure, evaluationOrder)
        if (unsupported !== undefined) {
          const sourceFile = path.relative(root, source.fileName)
          const startOffset = unsupported.getStart(source)
          const diagnostic = `Unsupported module initialization in ${sourceFile} at offset ${startOffset}`
          if (mode === 'checked') return reject('TOP_LEVEL_INITIALIZATION_UNSUPPORTED', diagnostic)
          initializationIssues.push({ sourceFile, startOffset, diagnostic })
        }
      }
    }

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

  const sortedFiles = [...files].sort()
  const resolution = resolver.freeze(sortedFiles)
  const result: LocalSourceClosure = resolution ? { files: sortedFiles, resolution } : { files: sortedFiles }
  if (mode === 'startup') result.initializationIssues = initializationIssues
  return result
}

function runtimeModuleSpecifiers(source: ts.SourceFile, options: ts.CompilerOptions): Set<string> {
  const emitted = ts.transpileModule(source.text, {
    fileName: source.fileName,
    compilerOptions: { ...options, target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext,
      noEmit: false, emitDeclarationOnly: false, declaration: false },
  }).outputText
  const javascript = ts.createSourceFile(`${source.fileName}.js`, emitted, ts.ScriptTarget.ES2022, true, ts.ScriptKind.JS)
  const specifiers = new Set<string>()
  for (const statement of javascript.statements) {
    if ((ts.isImportDeclaration(statement) || ts.isExportDeclaration(statement))
      && statement.moduleSpecifier && ts.isStringLiteral(statement.moduleSpecifier)) {
      specifiers.add(statement.moduleSpecifier.text)
    }
  }
  return specifiers
}

function importedBindingsUsedOnlyAsTypes(statement: ts.ImportDeclaration, source: ts.SourceFile): boolean {
  const clause = statement.importClause
  if (!clause) return false

  const names: string[] = []
  if (clause.name) names.push(clause.name.text)
  if (clause.namedBindings && ts.isNamedImports(clause.namedBindings)) {
    names.push(...clause.namedBindings.elements.map(element => element.name.text))
  } else if (clause.namedBindings && ts.isNamespaceImport(clause.namedBindings)) {
    names.push(clause.namedBindings.name.text)
  }
  if (names.length === 0) return false

  const typeUses = new Set<string>()
  const valueUses = new Set<string>()
  const visit = (node: ts.Node): void => {
    if (ts.isImportDeclaration(node)) return
    if (ts.isIdentifier(node) && names.includes(node.text)) {
      let ancestor: ts.Node | undefined = node.parent
      while (ancestor && ancestor !== source && !ts.isTypeNode(ancestor)) ancestor = ancestor.parent
      if (ancestor && ts.isTypeNode(ancestor)) typeUses.add(node.text)
      else valueUses.add(node.text)
    }
    ts.forEachChild(node, visit)
  }
  visit(source)
  return names.every(name => typeUses.has(name) && !valueUses.has(name))
}

/** Ordered DFS evaluates a dependency before its importer, without revisiting an active cycle. */
function moduleEvaluationOrder(dependencies: Map<string, string[]>, entry: string): Map<string, number> {
  const visited = new Set<string>()
  const order = new Map<string, number>()
  const visit = (file: string): void => {
    if (visited.has(file)) return
    visited.add(file)
    for (const dependency of dependencies.get(file) ?? []) visit(dependency)
    order.set(file, order.size)
  }
  visit(entry)
  return order
}

function isAmbientRuntimeDeclaration(statement: ts.Statement): boolean {
  if (!ts.isVariableStatement(statement) && !ts.isFunctionDeclaration(statement)
    && !ts.isClassDeclaration(statement) && !ts.isEnumDeclaration(statement)) return false
  return statement.modifiers?.some(modifier => modifier.kind === ts.SyntaxKind.DeclareKeyword) ?? false
}
