import { createHash } from 'node:crypto'
import { existsSync, readFileSync, realpathSync, statSync } from 'node:fs'
import path from 'node:path'
import ts from 'typescript'
import { resolveStartupRuntime, type RuntimeImportKind, type StartupRuntimeBoundary, type StartupRuntimeResolution } from './startup-runtime-resolution.js'

export interface FrozenModuleResolution {
  inputSha256: Record<string, string>
  tsconfig: string
  runtimeBoundaries?: StartupRuntimeBoundary[]
}

/** A bounded, offline runtime source resolver. Declaration files never stand in for executable code. */
export class FrozenModuleResolver {
  readonly options: ts.CompilerOptions
  private readonly inputs = new Set<string>()
  private readonly rawOptions: Record<string, unknown>
  private readonly aliases = new Map<string, string>()
  private readonly edges = new Map<string, Map<string, string>>()
  private readonly configStack = new Set<string>()
  private usedStartupResolution = false
  private readonly startupBoundaries = new Map<string, StartupRuntimeBoundary>()
  private pathsDirectory: string | undefined
  private readonly resolutionHost: ts.ModuleResolutionHost

  constructor(private readonly root: string, entry: string) {
    const preparedConfig = path.join(root, '.usvm-frozen.tsconfig.json')
    const config = existsSync(preparedConfig) ? preparedConfig
      : this.ancestors(path.dirname(entry)).map(directory => path.join(directory, 'tsconfig.json')).find(existsSync)
    this.rawOptions = config ? this.readConfig(config) : {
      target: 'ES2022', module: 'ESNext', moduleResolution: 'Node10', allowImportingTsExtensions: true,
    }
    if (this.rawOptions.paths && this.rawOptions.baseUrl === undefined) {
      const base = this.pathsDirectory ?? root
      this.rawOptions.paths = Object.fromEntries(Object.entries(this.rawOptions.paths as Record<string, string[]>).map(([key, values]) =>
        [key, values.map(value => path.relative(root, path.resolve(base, value)).split(path.sep).join('/'))]))
    }
    const converted = ts.convertCompilerOptionsFromJson(this.rawOptions, root)
    if (converted.errors.length > 0) throw new Error(ts.flattenDiagnosticMessageText(converted.errors[0]!.messageText, '\n'))
    this.options = converted.options
    this.resolutionHost = {
      getCurrentDirectory: () => root,
      fileExists: file => this.available(file) && statSync(file).isFile(),
      readFile: file => this.available(file) ? readFileSync(file, 'utf8') : undefined,
      directoryExists: directory => this.available(directory) && statSync(directory).isDirectory(),
      realpath: file => this.checkedPath(file),
    }
    this.recordMetadata(entry)
  }

  resolve(specifier: string, from: string): string {
    let target: string | undefined
    // TypeScript's paths/baseUrl precedence is also frozen for native execution.
    if (specifier.startsWith('.')) {
      const lexical = path.resolve(path.dirname(from), specifier)
      const directoryPackage = path.join(lexical, 'package.json')
      if (existsSync(directoryPackage)) {
        throw new Error(`Directory package resolution is unsupported: ${specifier}`)
      }
      if (lexical.endsWith('.js') && existsSync(lexical)) throw new Error(`JavaScript runtime source is unsupported: ${specifier}`)
      const candidates = (lexical.endsWith('.js') ? [lexical.slice(0, -3) + '.ts']
        : [lexical, `${lexical}.ts`, path.join(lexical, 'index.ts')])
        .filter(file => file.endsWith('.ts') && !file.endsWith('.d.ts') && existsSync(file) && statSync(file).isFile())
      if (candidates.length !== 1) throw new Error(`Expected one local TypeScript module for ${specifier}, found ${candidates.length}`)
      target = candidates[0]!
      if (realpathSync(target) !== target) throw new Error(`Relative dependency symlinks are unsupported: ${specifier}`)
    } else {
      // Do not let TS self-references/package imports bypass our runtime export checks.
      // This first lookup sees only paths/baseUrl and ordinary files, never package metadata.
      const aliasHost: ts.ModuleResolutionHost = {
        ...this.resolutionHost,
        fileExists: file => path.basename(file) !== 'package.json' && this.resolutionHost.fileExists(file),
        readFile: file => path.basename(file) === 'package.json' ? undefined : this.resolutionHost.readFile?.(file),
      }
      const resolved = ts.resolveModuleName(specifier, from, this.options, aliasHost).resolvedModule
      if (resolved && !resolved.isExternalLibraryImport) target = resolved.resolvedFileName
      else target = this.resolvePackage(specifier, from)
    }
    if (!target) throw new Error(`Unresolved runtime TypeScript module ${specifier} in ${path.relative(this.root, from)}`)
    target = this.checkedPath(target)
    if (!target.endsWith('.ts') || target.endsWith('.d.ts')) {
      throw new Error(`Runtime module is declaration-only or not TypeScript source: ${specifier} -> ${target}`)
    }
    // The frontend cannot assign stable project identities to physical node_modules files.
    // In-root workspace symlinks and source packages remain ordinary project sources.
    const installedPackageSource = path.relative(this.root, target).split(path.sep).includes('node_modules')
    if (installedPackageSource) {
      throw new Error(`Installed package runtime source is outside the supported workspace subset: ${specifier}`)
    }
    if (specifier.startsWith('.')) {
      const lexical = path.resolve(path.dirname(from), specifier)
      if (existsSync(lexical) && realpathSync(lexical) !== lexical) {
        throw new Error(`Relative dependency symlinks are unsupported: ${specifier}`)
      }
    } else {
      const previous = this.aliases.get(specifier)
      if (previous && previous !== target) throw new Error(`Context-dependent package resolution is unsupported: ${specifier}`)
      this.aliases.set(specifier, target)
    }
    this.recordMetadata(target)
    const bindings = this.edges.get(from) ?? new Map<string, string>()
    bindings.set(specifier, target)
    this.edges.set(from, bindings)
    return target
  }

  resolveStartup(specifier: string, from: string, importKind: RuntimeImportKind = 'import'): StartupRuntimeResolution {
    this.usedStartupResolution = true
    const result = resolveStartupRuntime(this.root, this.options, specifier, from, importKind,
      file => this.inputs.add(this.checkedPath(file)))
    if (result.kind === 'boundary') {
      this.startupBoundaries.set(`${from}\0${specifier}\0${importKind}`, result.boundary)
      if (result.boundary.runtimeFile) this.recordMetadata(path.join(this.root, result.boundary.runtimeFile))
      return result
    }
    const target = result.file
    this.recordMetadata(target)
    const bindings = this.edges.get(from) ?? new Map<string, string>()
    bindings.set(specifier, target)
    this.edges.set(from, bindings)
    if (!specifier.startsWith('.')) {
      const previous = this.aliases.get(specifier)
      if (previous && previous !== target) throw new Error(`Context-dependent runtime alias is unsupported: ${specifier}`)
      this.aliases.set(specifier, target)
    }
    return result
  }

  resolvedModule(specifier: string, from: string): ts.ResolvedModule | undefined {
    const target = this.edges.get(from)?.get(specifier)
    return target ? { resolvedFileName: target, isExternalLibraryImport: false } : undefined
  }

  freeze(files: string[]): FrozenModuleResolution | undefined {
    if (this.inputs.size === 0 && this.aliases.size === 0 && this.startupBoundaries.size === 0) return undefined
    const allInputs = [...new Set([...this.inputs, ...files.map(file => path.join(this.root, file))])].sort()
    const inputSha256 = Object.fromEntries(allInputs.map(file => [
      path.relative(this.root, file).split(path.sep).join('/'),
      createHash('sha256').update(readFileSync(file)).digest('hex'),
    ]))
    const paths = Object.fromEntries([...this.aliases].sort(([a], [b]) => a.localeCompare(b)).map(([specifier, target]) =>
      [specifier, [path.relative(this.root, target).split(path.sep).join('/')]]))
    const compilerOptions = { ...this.rawOptions, ...(this.usedStartupResolution ? { allowJs: true } : {}), baseUrl: '.', paths,
      types: [], noEmit: true, allowImportingTsExtensions: true }
    // This config deliberately roots only the inspected runtime closure; inherited include/exclude
    // cannot add unrelated project files to the coverage frontend.
    const resolution: FrozenModuleResolution = { inputSha256, tsconfig: JSON.stringify({ compilerOptions, files }, null, 2) + '\n' }
    if (this.startupBoundaries.size > 0) resolution.runtimeBoundaries = [...this.startupBoundaries.values()]
    return resolution
  }

  private resolvePackage(specifier: string, from: string): string | undefined {
    const parts = specifier.split('/')
    const packageName = specifier.startsWith('@') ? parts.slice(0, 2).join('/') : parts[0]!
    const subpath = parts.slice(packageName.startsWith('@') ? 2 : 1).join('/')
    for (const directory of this.ancestors(path.dirname(from))) {
      const own = path.join(directory, 'package.json')
      const installed = path.join(directory, 'node_modules', packageName, 'package.json')
      for (const metadata of [own, installed]) {
        if (!this.inside(metadata) || !existsSync(metadata)) continue
        const canonical = this.checkedPath(metadata)
        const pkg = JSON.parse(readFileSync(canonical, 'utf8')) as Record<string, unknown>
        if (metadata === own && pkg.name !== packageName) continue
        this.inputs.add(canonical)
        const exports = pkg.exports
        let entry: unknown
        if (exports !== undefined) {
          entry = typeof exports === 'object' && exports !== null && !Array.isArray(exports)
            && Object.keys(exports).some(key => key.startsWith('.'))
            ? (exports as Record<string, unknown>)[subpath ? `./${subpath}` : '.'] : subpath ? undefined : exports
        } else {
          entry = subpath ? `./${subpath}` : pkg.main
        }
        const runtimeEntry = this.runtimeExport(entry)
        if (typeof runtimeEntry !== 'string' || !runtimeEntry.startsWith('./')) return undefined
        const candidate = path.resolve(path.dirname(canonical), runtimeEntry)
        // Package metadata must point at actual runtime .ts code. A .js/.d.ts entry is never guessed as .ts.
        if (candidate.endsWith('.ts') && !candidate.endsWith('.d.ts') && existsSync(candidate)) return candidate
        return undefined
      }
    }
    return undefined
  }

  private runtimeExport(value: unknown): unknown {
    const select = (entry: unknown, mode: 'import' | 'require'): unknown => {
      if (typeof entry === 'string') return entry
      if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return undefined
      for (const [condition, nested] of Object.entries(entry)) {
        if (condition !== mode && condition !== 'node' && condition !== 'default') continue
        const selected = select(nested, mode)
        if (selected !== undefined) return selected
      }
      return undefined
    }
    // Respect package condition order, while rejecting distinct import/require implementations.
    const imported = select(value, 'import')
    const required = select(value, 'require')
    if (imported !== required) {
      throw new Error('Split import/require package exports are unsupported')
    }
    return imported
  }

  private readConfig(file: string): Record<string, unknown> {
    const canonical = this.checkedPath(file)
    if (this.configStack.has(canonical)) throw new Error('Cyclic tsconfig extends chain')
    this.configStack.add(canonical)
    this.inputs.add(canonical)
    this.recordMetadata(canonical)
    const loaded = ts.readConfigFile(canonical, file => readFileSync(file, 'utf8'))
    if (loaded.error) throw new Error(ts.flattenDiagnosticMessageText(loaded.error.messageText, '\n'))
    const config = loaded.config as { extends?: string | string[], compilerOptions?: Record<string, unknown> }
    let merged: Record<string, unknown> = {}
    const parents = config.extends ? Array.isArray(config.extends) ? config.extends : [config.extends] : []
    for (const parent of parents) {
      const parentFile = this.resolveConfig(parent, canonical)
      merged = { ...merged, ...this.readConfig(parentFile) }
    }
    const own = { ...config.compilerOptions }
    // Keep textual paths until the complete extends chain selects its effective baseUrl.
    // Without baseUrl, TypeScript roots paths at the config that declared them.
    if (own.paths && typeof own.paths === 'object') this.pathsDirectory = path.dirname(canonical)
    for (const key of ['baseUrl', 'rootDir', 'outDir', 'declarationDir']) {
      if (typeof own[key] === 'string') own[key] = path.relative(this.root, path.resolve(path.dirname(canonical), own[key] as string)) || '.'
    }
    if (Array.isArray(own.rootDirs)) own.rootDirs = own.rootDirs.map(value =>
      path.relative(this.root, path.resolve(path.dirname(canonical), String(value))))
    this.configStack.delete(canonical)
    return { ...merged, ...own }
  }

  private resolveConfig(specifier: string, from: string): string {
    let target: string | undefined
    if (specifier.startsWith('.') || path.isAbsolute(specifier)) {
      target = path.resolve(path.dirname(from), specifier)
    } else {
      const parts = specifier.split('/')
      const packageName = specifier.startsWith('@') ? parts.slice(0, 2).join('/') : parts[0]!
      const subpath = parts.slice(packageName.startsWith('@') ? 2 : 1).join('/')
      for (const directory of this.ancestors(path.dirname(from))) {
        const metadata = path.join(directory, 'node_modules', packageName, 'package.json')
        if (!this.available(metadata)) continue
        const canonical = this.checkedPath(metadata)
        const pkg = JSON.parse(readFileSync(canonical, 'utf8')) as { tsconfig?: unknown }
        this.inputs.add(canonical)
        const configured = typeof pkg.tsconfig === 'string' ? pkg.tsconfig : 'tsconfig.json'
        target = path.resolve(path.dirname(canonical), subpath || configured)
        break
      }
    }
    if (!target) throw new Error(`Missing frozen configuration package: ${specifier}`)
    if (!existsSync(target) && !target.endsWith('.json')) target += '.json'
    if (this.available(target) && statSync(target).isDirectory()) target = path.join(target, 'tsconfig.json')
    if (!this.available(target) || !statSync(target).isFile()) {
      throw new Error(`Missing frozen configuration source: ${specifier} -> ${target}`)
    }
    return this.checkedPath(target)
  }

  private recordMetadata(file: string): void {
    for (const directory of this.ancestors(path.dirname(file))) {
      for (const name of ['package.json', 'package-lock.json', 'npm-shrinkwrap.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml', 'yarn.lock', 'bun.lock']) {
        const metadata = path.join(directory, name)
        if (existsSync(metadata)) this.inputs.add(this.checkedPath(metadata))
      }
    }
  }

  private ancestors(directory: string): string[] {
    const result: string[] = []
    while (this.inside(directory)) {
      result.push(directory)
      if (directory === this.root) break
      directory = path.dirname(directory)
    }
    return result
  }

  private available(file: string): boolean {
    return this.inside(file) && existsSync(file) && this.inside(realpathSync(file))
  }

  private inside(file: string): boolean {
    const relative = path.relative(this.root, path.resolve(file))
    return relative === '' || relative !== '..' && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative)
  }

  private checkedPath(file: string): string {
    const canonical = realpathSync(file)
    if (!this.inside(file) || !this.inside(canonical)) throw new Error(`Module/configuration escapes frozen root: ${file}`)
    return canonical
  }
}
