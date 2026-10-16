import ts from 'typescript'
import { classBindingModified, uniqueDirectClassExport } from './class-coverage-source.js'

interface SavedClass { name: string; staticFields: string[] }
interface SavedBindings { exportName: string; classes: SavedClass[]; moduleBindings: string[] }
interface PreparedBindings { source: string | null; reason: string | null }

/** Add a bridge only in the private native overlay; the original source and journal stay unchanged. */
export function savedInputBindings(file: ts.SourceFile, request: SavedBindings): PreparedBindings {
  const unsupported = (reason: string): PreparedBindings => ({ source: null, reason })
  const identifier = (name: string): boolean => /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)
  const requestedNames = [...request.classes.map((entry) => entry.name), ...request.moduleBindings, request.exportName]
  if (!requestedNames.every(identifier)) return unsupported('Only ordinary top-level binding names are supported')
  if (new Set(requestedNames).size !== requestedNames.length) return unsupported('Replay binding names must be distinct')

  let bridgeCollision = false
  const checkBridgeName = (node: ts.Node): void => {
    if (ts.isIdentifier(node) && node.text.startsWith(request.exportName)) bridgeCollision = true
    ts.forEachChild(node, checkBridgeName)
  }
  checkBridgeName(file)
  if (bridgeCollision) return unsupported('The private replay bridge name already occurs in the source')

  const parameters = `${request.exportName}Arguments`
  const ownerArgument = `${parameters}[0]`
  const nameArgument = `${parameters}[1]`
  const valueArgument = `${parameters}[2]`
  const setters: string[] = []
  for (const entry of request.classes) {
    const matches = file.statements.filter(ts.isClassDeclaration).filter((node) => node.name?.text === entry.name)
    const selected = matches[0]
    if (matches.length !== 1 || selected === undefined || !uniqueDirectClassExport(file, selected, entry.name)) {
      return unsupported(`Exact top-level class binding is unavailable: ${entry.name}`)
    }
    if (classBindingModified(file, entry.name)) return unsupported(`Class binding is mutable: ${entry.name}`)
    const inherited = selected.heritageClauses?.some((clause) => clause.token === ts.SyntaxKind.ExtendsKeyword)
    const decorated = ts.canHaveDecorators(selected) && (ts.getDecorators(selected)?.length ?? 0) > 0
    if (inherited || decorated) return unsupported(`Inherited or decorated class state is unsupported: ${entry.name}`)

    for (const member of selected.members) {
      const privateName = member.name !== undefined && ts.isPrivateIdentifier(member.name)
      const memberDecorated = ts.canHaveDecorators(member) && (ts.getDecorators(member)?.length ?? 0) > 0
      const asynchronous = ts.canHaveModifiers(member)
        && (ts.getModifiers(member)?.some((modifier) => modifier.kind === ts.SyntaxKind.AsyncKeyword) ?? false)
      const generator = ts.isMethodDeclaration(member) && member.asteriskToken !== undefined
      if (privateName || memberDecorated || asynchronous || generator || ts.isClassStaticBlockDeclaration(member)) {
        return unsupported(`Class has unsupported private, decorated, async or static-block state: ${entry.name}`)
      }
    }

    for (const name of entry.staticFields) {
      const dataFields = selected.members.filter(ts.isPropertyDeclaration).filter((member) =>
        (ts.isIdentifier(member.name) || ts.isStringLiteral(member.name)) && member.name.text === name
        && (ts.getModifiers(member)?.some((modifier) => modifier.kind === ts.SyntaxKind.StaticKeyword) ?? false))
      if (dataFields.length !== 1) return unsupported(`Exact static data field is unavailable: ${entry.name}.${name}`)
      setters.push(`if (${ownerArgument} === ${JSON.stringify(entry.name)} && ${nameArgument} === ${JSON.stringify(name)}) {`
        + ` ${entry.name}[${JSON.stringify(name)}] = ${valueArgument}; return; }`)
    }
  }

  for (const name of request.moduleBindings) {
    const matches = file.statements.filter(ts.isVariableStatement).flatMap((statement) =>
      statement.declarationList.declarations.filter((declaration) =>
        ts.isIdentifier(declaration.name) && declaration.name.text === name)
        .map((declaration) => ({ statement, declaration })))
    const selected = matches[0]
    if (matches.length !== 1 || selected === undefined
      || (selected.statement.declarationList.flags & ts.NodeFlags.Const) !== 0) {
      return unsupported(`Mutable top-level module binding is unavailable: ${name}`)
    }
    setters.push(`if (${ownerArgument} === '%dflt' && ${nameArgument} === ${JSON.stringify(name)}) {`
      + ` ${name} = ${valueArgument}; return; }`)
  }

  const classes = request.classes.map((entry) => `[${JSON.stringify(entry.name)}]: ${entry.name}`).join(', ')
  const bridge = `\nexport const ${request.exportName} = { classes: { ${classes} },\n`
    + `  writeBinding(...${parameters}: any[]): void {\n${setters.join('\n')}\n`
    + `    throw Error('Unknown saved input environment binding: ' + ${ownerArgument} + '.' + ${nameArgument});\n  }\n};\n`
  return { source: file.text + bridge, reason: null }
}
