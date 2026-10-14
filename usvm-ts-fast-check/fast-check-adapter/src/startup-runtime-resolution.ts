import { existsSync, readFileSync, realpathSync, statSync } from 'node:fs'
import { isBuiltin } from 'node:module'
import path from 'node:path'
import ts from 'typescript'

export type RuntimeImportKind = 'import' | 'require'
export interface StartupRuntimeBoundary {
  sourceFile: string
  specifier: string
  importKind: RuntimeImportKind
  kind: 'node-builtin' | 'json'
  runtimeFile?: string
}
export type StartupRuntimeResolution = { kind: 'source', file: string }
  | { kind: 'boundary', boundary: StartupRuntimeBoundary }

/** Actual acquired runtime bytes and explicit boundaries, without an engine-support claim. */
export function resolveStartupRuntime(
  root: string, options: ts.CompilerOptions, specifier: string, from: string,
  importKind: RuntimeImportKind, recordInput: (file: string) => void,
): StartupRuntimeResolution {
  const relative = (file: string): string => path.relative(root, file).split(path.sep).join('/')
  const inside = (file: string): boolean => {
    const name = path.relative(root, path.resolve(file))
    return name === '' || name !== '..' && !name.startsWith(`..${path.sep}`) && !path.isAbsolute(name)
  }
  const available = (file: string): boolean => inside(file) && existsSync(file) && inside(realpathSync(file))
  const checked = (file: string): string => {
    if (!available(file)) throw new Error(`Runtime module missing or outside frozen root: ${file}`)
    return realpathSync(file)
  }
  const readPackage = (file: string): Record<string, unknown> => {
    const canonical = checked(file)
    recordInput(canonical)
    return JSON.parse(readFileSync(canonical, 'utf8')) as Record<string, unknown>
  }
  const condition = (value: unknown): unknown => {
    if (typeof value === 'string') return value
    if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
    for (const [name, entry] of Object.entries(value)) {
      if (name !== importKind && name !== 'node' && name !== 'default') continue
      const selected = condition(entry)
      if (selected !== undefined) return selected
    }
    return undefined
  }
  const selectSubpath = (value: unknown, key: string): unknown => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return key === '.' ? value : undefined
    const entries = value as Record<string, unknown>
    if (!Object.keys(entries).some(name => name.startsWith('.'))) return key === '.' ? value : undefined
    if (Object.hasOwn(entries, key)) return entries[key]
    const patterns = Object.keys(entries).filter(name => name.includes('*')).sort((a, b) =>
      b.indexOf('*') - a.indexOf('*') || b.length - a.length)
    for (const pattern of patterns) {
      const [before, after] = pattern.split('*')
      if (pattern.split('*').length !== 2 || !key.startsWith(before!) || !key.endsWith(after!)) continue
      const wildcard = key.slice(before!.length, key.length - after!.length)
      const replace = (entry: unknown): unknown => typeof entry === 'string' ? entry.replaceAll('*', wildcard)
        : entry && typeof entry === 'object' && !Array.isArray(entry)
          ? Object.fromEntries(Object.entries(entry).map(([name, nested]) => [name, replace(nested)])) : entry
      return replace(entries[pattern])
    }
    return undefined
  }
  const resolveFile = (candidate: string, seen = new Set<string>()): string | undefined => {
    if (!inside(candidate) || seen.has(candidate)) return undefined
    seen.add(candidate)
    if (available(candidate) && statSync(candidate).isFile()) return checked(candidate)
    const suffix = path.extname(candidate)
    const sourceFallback = suffix === '.js' ? candidate.slice(0, -3) + '.ts'
      : suffix === '.mjs' ? candidate.slice(0, -4) + '.mts'
        : suffix === '.cjs' ? candidate.slice(0, -4) + '.cts' : undefined
    if (sourceFallback && available(sourceFallback)) return checked(sourceFallback)
    if (!['.ts', '.tsx', '.mts', '.cts', '.js', '.jsx', '.mjs', '.cjs', '.json'].includes(suffix)) {
      const candidates = ['.ts', '.tsx', '.mts', '.cts', '.js', '.jsx', '.mjs', '.cjs']
        .map(extension => candidate + extension).filter(available)
      if (candidates.length > 1) throw new Error(`Ambiguous extensionless runtime module: ${candidate}`)
      if (candidates.length === 1) return checked(candidates[0]!)
    }
    if (available(candidate) && statSync(candidate).isDirectory()) {
      const metadata = path.join(candidate, 'package.json')
      if (available(metadata)) {
        const pkg = readPackage(metadata)
        if (typeof pkg.main === 'string') {
          const target = resolveFile(path.resolve(candidate, pkg.main), seen)
          if (target) return target
        }
      }
      return resolveFile(path.join(candidate, 'index'), seen)
    }
    return undefined
  }

  from = checked(from)
  if (isBuiltin(specifier)) {
    return { kind: 'boundary', boundary: { sourceFile: relative(from), specifier, importKind, kind: 'node-builtin' } }
  }

  let target: string | undefined
  if (specifier.startsWith('.')) {
    target = resolveFile(path.resolve(path.dirname(from), specifier))
  } else {
    // Config aliases take precedence. Package metadata must choose runtime rather than declaration files.
    const host: ts.ModuleResolutionHost = {
      fileExists: file => path.basename(file) !== 'package.json' && !/\.d\.[cm]?ts$/.test(file)
        && available(file) && statSync(file).isFile(),
      readFile: () => undefined,
      directoryExists: file => available(file) && statSync(file).isDirectory(),
      realpath: checked,
    }
    const alias = ts.resolveModuleName(specifier, from, { ...options, allowJs: true }, host).resolvedModule
    if (alias && !alias.isExternalLibraryImport) target = alias.resolvedFileName
    if (!target) {
      const parts = specifier.split('/')
      const packageName = specifier.startsWith('@') ? parts.slice(0, 2).join('/') : parts[0]!
      const subpath = parts.slice(packageName.startsWith('@') ? 2 : 1).join('/')
      let directory = path.dirname(from)
      while (inside(directory) && !target) {
        const own = path.join(directory, 'package.json')
        const installed = path.join(directory, 'node_modules', packageName, 'package.json')
        for (const metadata of [own, installed]) {
          if (!available(metadata)) continue
          const pkg = readPackage(metadata)
          if (metadata === own && pkg.name !== packageName) continue
          const packageRoot = path.dirname(checked(metadata))
          const exported = pkg.exports === undefined ? undefined
            : condition(selectSubpath(pkg.exports, subpath ? `./${subpath}` : '.'))
          if (pkg.exports !== undefined && (typeof exported !== 'string' || !exported.startsWith('./'))) {
            throw new Error(`No ${importKind} runtime package export for ${specifier}`)
          }
          const entry = exported ?? (subpath || pkg.main || 'index')
          if (typeof entry !== 'string') throw new Error(`Invalid runtime package entry for ${specifier}`)
          const candidate = path.resolve(packageRoot, entry)
          const packageRelative = path.relative(packageRoot, candidate)
          if (packageRelative === '..' || packageRelative.startsWith(`..${path.sep}`) || path.isAbsolute(packageRelative)) {
            throw new Error(`Runtime package entry escapes its package: ${specifier}`)
          }
          const exactExportExists = available(candidate) && statSync(candidate).isFile()
          target = exported !== undefined ? exactExportExists ? checked(candidate) : undefined : resolveFile(candidate)
          if (!target) throw new Error(`Missing acquired ${importKind} runtime entry for ${specifier}`)
          break
        }
        if (directory === root) break
        directory = path.dirname(directory)
      }
    }
  }
  if (!target) throw new Error(`Unresolved acquired runtime module ${specifier} in ${relative(from)}`)
  target = checked(target)
  recordInput(target)
  if (target.endsWith('.json')) {
    JSON.parse(readFileSync(target, 'utf8'))
    return { kind: 'boundary', boundary: { sourceFile: relative(from), specifier, importKind,
      kind: 'json', runtimeFile: relative(target) } }
  }
  if (/\.d\.[cm]?ts$/.test(target) || !/\.[cm]?[jt]sx?$/.test(target)) {
    throw new Error(`Acquired runtime module is declaration-only or unsupported: ${specifier} -> ${target}`)
  }
  return { kind: 'source', file: target }
}
