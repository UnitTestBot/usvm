# Исполнимый план развития гибридного PBT и symbolic execution

Дата: 2026-07-19.

Этот документ отвечает на два вопроса:

1. какой результат фактически получен сейчас;
2. как независимые агенты должны довести прототип до честно измеряемой
   гибридной системы, не смешивая оптимизацию orchestration с исправлением
   семантики и replay.

План продолжает `06-external-tools-integration-plan.md`, использует измерения
из `11-representative-open-source-campaign.md` и
`12-batched-entrypoint-rerun.md` и заменяет их списки следующих шагов единым
task DAG. Существующий concrete EtsIR interpreter и `usvm-ts` сохраняются.

## 1. Какой результат получен

### 1.1. Широкая выборка реальных source entry points

На 84 экспортируемых source entry points из трёх закреплённых open-source
проектов, содержащих 422 EtsIR branch edges:

| Конфигурация | Coverage | Symbolic reached | Replay-confirmed targets | Время |
|---|---:|---:|---:|---:|
| internal PBT | 305/422 (72.27%) | — | — | 1.419 s |
| USVM отдельно | 240/422 (56.87%) | 238/422 | 207/422 | 138.703 s |
| internal PBT → USVM | **309/422 (73.22%)** | 25/117 residual | 2/117 residual | 19.775 s total, 18.897 s symbolic |

Итого USVM даёт строгий replay-подтверждённый прирост после PBT: **+4 ветви,
+0.95 pp**. Два replay-confirmed residual witness покрыли четыре branch edges,
потому что один запуск может попутно пройти несколько рёбер.

Это полезный, но пока дорогой top-up. Низкий hybrid gain нельзя объяснить тем,
что USVM вообще не ищет пути: standalone он покрывает 240 ветвей. После PBT ему
остаётся значительно более сложный по семантике хвост.

### 1.2. Общий primitive denominator для всех готовых инструментов

На 42 однозначно сопоставленных source entry points и 236 branch edges:

| Инструмент | Standalone coverage | После USVM | Прирост USVM |
|---|---:|---:|---:|
| internal PBT | 217/236 (91.95%) | 220/236 | +3 |
| fast-check | 213/236 (90.25%) | 216/236 | +3 |
| Jazzer.js | 211/236 (89.41%) | 218/236 | +7 |
| ExpoSE | 162/236 (68.64%) | 202/236 | **+40** |
| внешний ensemble | 220/236 (93.22%) | 220/236 | 0 |
| USVM отдельно | 169/236 (71.61%) | — | — |

На этом поддерживаемом subset replay fidelity самого USVM высокая:
**147 reached / 144 replay-confirmed**. На residual после ExpoSE —
**34 reached / 33 replay-confirmed**. Следовательно, Yices и базовая модель
успешно работают на поддерживаемой арифметике; менять solver сейчас оснований
нет.

### 1.3. Что показало пакетирование target roots

Передача независимых target roots списком в одну машину архитектурно
корректна. `UTargetsSet.from` хранит список активных roots, а общий target
machinery распространяет и удаляет их. Он не строит trie автоматически:
`TaintAnalysis` также индексирует уже построенные target trees. Поэтому для
конкретного ребра остаётся явная смысловая цепочка
`entry → if statement → successor`, а искусственный общий root не нужен.

На том же primitive subset:

| Метрика | Один target на запуск | Один batch на метод | Изменение |
|---|---:|---:|---:|
| Machine runs | 136 | 63 | −53.7% |
| Symbolic wall time | 39.498 s | 67.782 s | **+71.6%** |
| ExpoSE → USVM | 203/236 | 202/236 | −1 ветвь |

Хвост масштабируется почти точно как `N × perTargetTimeout`: наблюдались
batch'и `14 targets / 14.003 s`, `12 / 12.035 s`, `8 / 8.014 s`, `5 / 5.004 s`.
`stopOnTargetsReached` завершает работу только после удаления всех roots, а
текущий safety timeout равен `N × perTargetTimeout`. Один недостижимый root
удерживает весь batch.

Вывод: **список targets оставляем**, но монолитный batch без progress stop и
replay-pruning не является оптимизацией производительности.

## 2. Root-cause tree

Ниже причины намеренно разделены. Ускорение target scheduler не исправит
ложную symbolic модель, а новый builtin не устранит `N × timeout`.

```text
Малый и дорогой replay-confirmed hybrid gain
├── O. Orchestration / scheduling
│   ├── O1. stopOnTargetsReached ждёт удаления всех roots
│   ├── O2. batch ceiling = N × perTargetTimeout
│   ├── O3. TARGETED выбирает min-distance без fairness между roots
│   ├── O4. active targets сканируются на каждом выборе/propagation
│   ├── O5. ReachabilityObserver имеет стоимость O(states × active targets)
│   ├── O6. TsMachine не имеет JVM TargetsReachableForkBlackList
│   └── O7. batch не replay-prune'ит попутно покрытые edges
│
├── S. Semantic / model extraction / replay fidelity
│   ├── S1. module init и namespace/import binding
│   ├── S2. materialization и dispatch callable/function values
│   ├── S3. iterator protocol, Symbol.iterator, for-of
│   ├── S4. static/runtime builtins и .call
│   ├── S5. object membership, hasOwnProperty, Map.get и truthiness
│   └── S6. symbolic/concrete implementation parity
│
├── C. Capability selection
│   ├── C1. несовместимые targets всё равно получают Yices budget
│   ├── C2. unsupported узнаётся слишком поздно, после exploration
│   └── C3. нет стабильного reason code и feature-profile для target prefix
│
└── M. Measurement / mapping / adapter surface
    ├── M1. broad и primitive denominators имеют разные возможности harness'ов
    ├── M2. native source coverage неоднозначно сопоставляется с EtsIR edge
    ├── M3. не все инструменты экспортируют одинаково выразимые значения
    ├── M4. process startup и corpus generation ранее считались неодинаково
    └── M5. один seed не даёт confidence interval
```

### 2.1. Доказательство, что O и S — разные проблемы

В broad hybrid USVM достиг 25 residual targets, но replay подтвердил только 2.
Оставшиеся 23 разрыва уже разложены без категории `unknown`:

| Семантический класс | Reached, но не replayed |
|---|---:|
| iterator / `for-of` | 9 |
| namespace `util.defaultEquals` и callable materialization | 11 |
| `Array.isArray` / static runtime | 2 |
| `Map.get` / membership / truthiness | 1 |
| **Итого** | **23** |

На `typescript-collections` особенно явно виден semantic gap: 14 targets были
symbolically reached, но 0 были replay-confirmed. Наблюдались:

- 500/500 throws в array API из-за `defaultEquals === undefined`;
- 25/25 `Unsupported Symbol.iterator` в `forEach`;
- пути через `Object.prototype.toString.call`,
  `Object.prototype.hasOwnProperty.call` и `Map.get`.

Progress stop ускорит эти 23 неуспешных задачи, но сам по себе не превратит их
в concrete witnesses. Semantic packages должны иметь отдельные критерии
приёмки.

## 3. Неподвижные инварианты проекта

1. Coverage truth — только concrete replay в EtsIR interpreter. Native V8,
   Istanbul, Jalangi, SynTest или Gillian coverage является диагностикой до
   replay и не увеличивает итоговый numerator.
2. Solver остаётся **Yices** до завершения orchestration и fidelity ablations.
3. Source entry point выбирается по сохранённому source→EtsIR mapping. Lowered
   callbacks/closures не становятся самостоятельными entry points.
4. `reached`, `model-extracted`, `replay-executed`, `edge-confirmed` — разные
   события и разные счётчики.
5. На broad denominator unsupported/incompatible **остаётся uncovered**. Он
   получает явный capability label, но не исчезает из denominator. Исключение
   допустимо только для заранее названного и замороженного supported/common
   denominator, который публикуется вместе с support rate относительно broad.
6. Ни один инструмент не оценивается по raw path count.
7. Новая оптимизация сначала включается feature flag'ом и сравнивается с
   сохранённым legacy режимом.
8. Изменения concrete и symbolic semantics сопровождаются differential fixture:
   Node.js ↔ concrete EtsIR ↔ symbolic witness replay.

Формулы считаются по множествам стабильных EtsIR edge IDs, без округления
отображаемых процентов:

```text
broadCoverage(tool) = |confirmedEdges(tool) ∩ D_broad| / |D_broad|
supportedCoverage(tool) = |confirmedEdges(tool) ∩ D_supported| / |D_supported|
supportRate = |D_supported| / |D_broad|
hybridGain(A, B) = |confirmedEdges(A ∪ B) ∩ D| - |confirmedEdges(A) ∩ D|
```

`D_broad` сейчас равен 422 edges. `D_supported` фиксируется capability-аудитом
до запуска сравниваемых инструментов; исключать edge после неудачного run
запрещено. Все четыре величины — numerator, denominator, unsupported count и
support rate — показываются рядом.

## 4. Модель исполнения агентами

### 4.1. Правило координатора

Root/coordinator не реализует пакеты сам. Он:

- создаёт отдельные worktrees;
- выдаёт агенту один bounded work package и список принадлежащих ему файлов;
- принимает commit только после локальных тестов и gate report;
- интегрирует готовые commits в порядке DAG;
- не разрешает двум активным агентам изменять один файл.

При четырёх доступных слотах одновременно работают coordinator и не более
трёх implementation agents. Следующая тройка запускается сразу после
освобождения слота.

### 4.2. Worktree и commit discipline

Для каждого package создаётся worktree от одного зафиксированного integration
base:

```text
.worktrees/ts-pbt-<agent-id>
branch: caelmbleidd/ts-pbt-<agent-id>
```

Правила:

- один package — один или несколько небольших commits, но без unrelated fixes;
- один commit не пересекает file ownership другого агента;
- перед началом агент записывает `git status --short` и base SHA в handoff;
- никакого `git reset --hard`, переписывания чужих веток или массового
  форматирования;
- merge/rebase делает только coordinator после зелёных package tests;
- attribution trailers не добавляются;
- внешние forks хранятся в отдельных репозиториях/worktrees, их лицензии,
  `LICENSE` и `NOTICE` сохраняются.

### 4.3. Защита текущего dirty worktree

На момент составления плана в текущем worktree есть пользовательские изменения:

```text
usvm-ts-pbt/.../interpreter/Intrinsics.kt
usvm-ts/.../expr/CallApproximations.kt
usvm-ts/.../expr/TsExprResolver.kt
usvm-ts/.../interpreter/TsInterpreter.kt
2026-07-17-122150-property-based-testing.txt
docs/ts-pbt/05-real-world-problems.md
```

**Запрещено** модифицировать, stash'ить, добавлять в index или коммитить эти
пути из текущего worktree. До semantic integration coordinator должен:

1. зафиксировать точный diff и его владельца;
2. либо получить отдельный baseline commit от владельца, либо перенести diff в
   защищённую ветку;
3. назначить единственного владельца каждому из четырёх Kotlin-файлов;
4. только затем создать semantic worktree от нового base.

В этом плане владельцем `Intrinsics.kt` становится `A-SEM-CONCRETE`, а трёх
symbolic-файлов — `A-SEM-SYMBOLIC`, но это назначение вступает в силу только
после выполнения трёх шагов выше. Текстовый лог и `05-real-world-problems.md`
implementation agents не трогают вообще.

### 4.4. Сводная таблица непересекающегося file ownership

Путь теста принадлежит тому же агенту, что соответствующий production path.
Любой файл вне таблицы требует переназначения coordinator'ом до редактирования.

| Agent | Исключительное владение |
|---|---|
| `A-BASE` | baseline manifest и upstream-audit table, без product code |
| `A-CONTRACT` | artifact schemas/validator, `ExternalTestCorpus.kt`, `TargetManifest.kt` |
| `A-REPLAY` | новый единый Kotlin replay pipeline/CLI и его tests |
| `A-TEL` | `telemetry/**`, `HybridReport.kt`, `telemetry-summary.mjs` |
| `A-ORCH-P` | новый generic progress stop в `usvm-core` |
| `A-ORCH-S` | `SymbolicPhase.kt`, новый `TargetShardPlanner.kt` |
| `A-ORCH-F` | `TsMachine.kt`, новый TS fork-pruning adapter |
| `A-MAP` | mapping classes, `source-targets.cjs`, jacodb origin-sidecar patch |
| `A-CAP` | новый `capability/**` |
| `A-SEM-MOD` | dependency-neutral module spec/fixtures |
| `A-SEM-CALL` | dependency-neutral callable spec/fixtures |
| `A-SEM-ITER` | dependency-neutral iterator spec/fixtures |
| `A-SEM-BLT` | dependency-neutral builtin spec/fixtures |
| `A-SEM-CONCRETE` | `Intrinsics.kt`, `EtsConcreteInterpreter.kt`, `CallResolver.kt`, `VValue.kt` после dirty handoff |
| `A-SEM-SYMBOLIC` | `TsInterpreter.kt`, `TsExprResolver.kt`, `CallApproximations.kt` после dirty handoff |
| `A-ROUTER` | новый `CapabilityRouter.kt` и router config |
| `A-EXT-FC` | `external-tools/fast-check-adapter/**` |
| `A-EXT-JZ` | `external-tools/jazzer-adapter/**` |
| `A-EXT-SYN` | новый `external-tools/syntest-adapter/**` |
| `A-EXT-NM` | новый `external-tools/nodemedic-adapter/**` |
| `A-EXT-EXP` | `external-tools/expose-adapter/**` |
| `A-EXT-GIL` | `external-tools/gillian-adapter/**` |
| `A-INT` | `Main.kt`, top-level Gradle wiring, `benchmarks/README.md`, unified launcher |
| `A-BENCH` | `run-multiseed-campaign.*`, `summarize-multiseed.*`, новый result directory |
| `A-DOC` | этот roadmap, upstream audit publication и final analytical report |

### 4.5. Обязательный handoff каждого implementation agent

Каждый агент возвращает coordinator'у один и тот же набор:

1. branch, base SHA и commit SHA без attribution trailers;
2. список изменённых файлов, совпадающий с ownership;
3. выполненные unit/integration/differential tests и их точные команды;
4. до/после для численного acceptance gate;
5. включённый и выключенный feature-flag smoke-test;
6. rollback note: какой commit revert'ить и какие artifacts несовместимы;
7. известные ограничения; capability `unknown` допустим только до обязательного
   dynamic probe и после probe должен получить terminal outcome.

Commit не интегрируется, если хотя бы один пункт отсутствует. Benchmark agent
не исправляет product code по ходу кампании, а возвращает отдельный defect
handoff следующему владельцу.

## 5. Artifact contract: adapter отдельно, replay отдельно

Schema фиксируется как **`schemaVersion: 2`** до реализации adapters. Один
`A-CONTRACT` владеет схемами и validator'ом; `A-REPLAY` владеет единственным
Kotlin replay. External adapters не вычисляют EtsIR coverage и не создают
`replay-report`: они отдают только raw corpus, native diagnostics и metadata.

### 5.1. Общие входы adapter'а

| Файл | Формат и назначение |
|---|---|
| `target-manifest.json` | JSON document: stable method IDs, signatures и parameter schema |
| `source-targets.jsonl` | ровно одна JSONL-запись на EtsIR edge |
| `method-ids.txt` | заранее замороженный denominator запуска |
| `initial.etc.jsonl` | optional corpus; flag полностью опускается, если файла нет |
| `run-config.json` | schema v2, seed, deadline, versions, commits, cold/warm и flags |

Каждая edge-запись `source-targets.jsonl` содержит как минимум `methodId`,
`branchId`, `stmtIndex`, `successorStmtIndex`, `successorOrdinal`, TS source
range, emitted JS range при наличии, source callable/module origin и строго один
mapping status:

```text
exact | oneToMany | ambiguous | unmapped | synthetic
```

Capabilities не встраиваются в mapping. Они записываются отдельно в
`capability-report.jsonl` с ключом `(methodId, branchId)`.

ETC v2 использует JSONL и различает `undefined`, `null`, `NaN`, infinities,
`-0`, array holes, objects, arrays, maps/sets и explicit unrepresentable
function/cycle. Structured значения могут иметь receiver/constructor plan,
callable reference и alias ID. `A-CONTRACT` реализует явный converter current
v1 → v2 и schema-compatible чтение обоих форматов; второго параллельного v2
формата нет.

### 5.2. Единый adapter CLI и raw outputs

```text
<adapter> run
  --target-manifest target-manifest.json
  --source-targets source-targets.jsonl
  --method-ids method-ids.txt
  [--initial-etc initial.etc.jsonl]
  --seed <unsigned-int>
  --budget-ms <end-to-end-budget>
  --export-replay-grace-ms <reserved-grace>
  --out-dir <run-directory>
```

Optional flag не передаётся вообще, если input отсутствует. `stdout` содержит
только protocol JSON events; diagnostics идут в `stderr.log`, который
ротируется/обрезается по фиксированному cap (по умолчанию 16 MiB) с явным
`logTruncated=true`. Exit status различает success, unsupported configuration,
tool failure и timeout-with-partial-corpus.

Adapter обязан создать только:

| Файл | Содержание |
|---|---|
| `corpus.etc.jsonl` | concrete cases, producer/version, `generatedAtMs`, seed/path |
| `native-coverage.json` | native claims, никогда не итоговый coverage |
| `run-meta.json` | startup/generation/export time, commits, exit, timeout, log cap |
| `stderr.log` | bounded diagnostics |

`generatedAtMs` — monotonic milliseconds от общего старта run, когда case
стал доступен для экспорта, а не timestamp последующей сериализации.

### 5.3. Единый Kotlin replay

`A-REPLAY` читает raw output любого adapter'а одной командой и создаёт:

| Файл | Содержание |
|---|---|
| `replay-report.jsonl` | одна запись на case/edge outcome |
| `residual-targets.jsonl` | edges denominator, ещё не confirmed replay |
| `mapping-report.json` | exact/oneToMany/ambiguous/unmapped/synthetic counts |
| `capability-report.jsonl` | static label, dynamic probe и terminal capability status |
| `deadline-report.json` | budget, grace, over-budget и late outcomes |

Для впервые подтверждённого edge replay пишет `discoveredAtMs`: monotonic
время завершения **инкрементального** EtsIR replay этого case от общего старта.
Cases реплеятся в порядке `(generatedAtMs, caseId)` по мере доступности; AUC в
момент `t` использует только edges с `discoveredAtMs ≤ t`. Если tool экспортирует
только финальный batch, кривая честно остаётся плоской до export/replay.

Инвариант:

```text
confirmed ⊆ replayExecuted ⊆ imported − rejected
```

Каждый reject и replay failure имеет reason code. Capability `needs_dynamic_probe`
допустим на статическом этапе, но после обязательного probe получает terminal
status; только после этого финальный `unknown` обязан быть равен нулю.

### 5.4. Deadline controller

Для общего end-to-end budget `B` заранее резервируется одинаковый для всех
tools grace:

```text
G = min(5000 ms, max(1000 ms, floor(0.10 × B)))
explorationDeadline = B - G
hardResultDeadline = B
```

В `G` входят остановка, export и incremental replay. После `B` controller
может завершить безопасный flush, но пишет `overBudgetMs`; late cases/edges
сохраняются как diagnostics и **не входят** в fixed-budget coverage/AUC.
Timeout обязан сохранить partial corpus. Campaign отдельно показывает долю
неуспевшего replay, чтобы маленький grace не превратился в скрытое преимущество.

## 6. Task DAG

```text
W0  BASE ─┬─> CONTRACT ─> REPLAY ──────────────────────────────────┐
          ├─> MAP ──────> CAP-SCAN ─> ROUTER ─────────────────────┤
          ├─> TEL ──────> ORCH-PROGRESS ─> ORCH-SHARDS ──────────┤
          └───────────────────────────────> ORCH-PRUNE ───────────┤
                                                                   │
W1  SEM-SPECS(module/callable/iterator/builtins)                    │
          ├─> SEM-CONCRETE ─┐                                     │
          └─> SEM-SYMBOLIC ─┴─> FIDELITY-GATE ────────────────────┤
                                                                   │
W2  CONTRACT + REPLAY + MAP ─┬─> FASTCHECK ───────────────────────┤
                             ├─> JAZZER ──────────────────────────┤
                             ├─> SYNTEST ─────────────────────────┤
                             ├─> NODEMEDIC ───────────────────────┤
                             ├─> EXPOSE ──────────────────────────┤
                             └─> GILLIAN ─────────────────────────┤
                                                                   │
W3  all gates ─> INTEGRATION ─> ABLATIONS ─> CAMPAIGN ─> DOC ─────┘
```

`ORCH-*` можно разрабатывать параллельно с semantic specs: их file ownership
не пересекается. Adapter agent стартует только после schema-v2 validator,
единого replay smoke-test и mapping fixture. Campaign начинается после
интеграционного smoke-test raw-adapter → Kotlin-replay для каждого tool.

## 7. Work packages

### WP-BASE — заморозка baseline

**Agent:** `A-BASE` (coordinator-only, без product edits).

**Deliverables:**

- base SHA, dirty-path audit и закреплённые project commits;
- копия compact benchmark JSON/CSV;
- списки 84/422 broad и 42/236 primitive;
- одна команда воспроизведения каждого baseline;
- таблица версий JDK, Node, Yices, ts-frontend и adapters;
- заново измеренный paired legacy timing baseline на той же машине: historical
  `39.498 s` хранится только как справка, а не acceptance threshold;
- upstream audit: canonical URL, pinned commit/version, SPDX identifier и
  required `LICENSE/NOTICE` для каждого tool.

**Acceptance:** повторная агрегация даёт ровно 305/422, 240/422, 309/422 и
primitive counts из §1 либо документирует воспроизводимое отличие до начала
разработки. Ни один adapter run не начинается, пока его audit row не заполнен;
неизвестный commit/URL/SPDX является явным deliverable `A-BASE`, а не
догадкой в этом плане.

Начальная audit table:

| Tool | Canonical URL | Pinned revision | SPDX | Статус |
|---|---|---|---|---|
| fast-check | `https://github.com/dubzzz/fast-check` | `4.9.0`, commit зафиксировать | проверить audit | task output `A-BASE` |
| Jazzer.js | `https://github.com/CodeIntelligenceTesting/jazzer.js` | `4.0.0`, commit зафиксировать | проверить audit | task output `A-BASE` |
| ExpoSE | `https://github.com/ExpoSEJS/ExpoSE` | `ec03edf85f883248612b1d498c6a7d9189d16d6f` | проверить audit | revision известна, license audit обязателен |
| Gillian | canonical upstream URL зафиксировать | `b195dfc3`, полный SHA зафиксировать | `BSD-3-Clause`, перепроверить | task output `A-BASE` |
| SynTest-JavaScript | canonical upstream URL зафиксировать | зафиксировать до pilot | `Apache-2.0`, перепроверить | task output `A-BASE` |
| NodeMedic-FINE | canonical upstream URL зафиксировать | зафиксировать до pilot | `MIT`, перепроверить | task output `A-BASE` |

### WP-CONTRACT — schema v2 и artifact validator

**Agent:** `A-CONTRACT`.

**Dependencies:** WP-BASE.

**Владение:** `ExternalTestCorpus.kt`, `TargetManifest.kt`, новые artifact
schema/validator classes и contract fixtures.

**Deliverables:** frozen v2 schemas из §5, v1→v2 converter, CLI validator,
golden valid/invalid raw run directories, schema compatibility policy.

**Acceptance:** один format на artifact; ETC special values и edge records
проходят round-trip; все unknown fields обрабатываются по документированной
policy; malformed/unknown schema version даёт explicit reject; ни один adapter
не имеет собственной копии ETC codec.

### WP-REPLAY — единый Kotlin replay и incremental timestamps

**Agent:** `A-REPLAY`.

**Dependencies:** WP-CONTRACT, WP-MAP для production mapping; до WP-MAP
разрабатывается на frozen fixtures.

**Владение:** новый Kotlin replay pipeline/CLI и его tests. Adapter directories
не менять.

**Deliverables:** raw corpus → `replay-report.jsonl`,
`residual-targets.jsonl`, `mapping-report.json`, `deadline-report.json`;
incremental replay ordering и `discoveredAtMs`.

**Acceptance:** один и тот же raw corpus от двух producer labels даёт одинаковый
EtsIR coverage; `confirmed ⊆ replayExecuted ⊆ imported − rejected`; late result
не входит в fixed-budget numerator/AUC; old in-process replay и новый CLI
совпадают на primitive golden corpus.

### WP-TEL — telemetry и failure taxonomy

**Agent:** `A-TEL`.

**Владение файлами:**

- новый пакет `usvm-ts-pbt/.../telemetry/**`;
- `report/HybridReport.kt`;
- новые telemetry tests;
- один новый benchmark summarizer, не существующие campaign scripts.

**Задачи:**

1. Ввести стабильные reason codes:
   `timeout_no_progress`, `global_safety_timeout`, `unreachable_pruned`,
   `solver_reached`, `model_extraction_failed`, `replay_unsupported`,
   `replay_diverged`, `replay_wrong_edge`, `confirmed`.
2. Записывать timestamps: machine start, last terminal progress, target reach,
   model extraction, replay finish.
3. Писать active root count, shard ID, states, steps, solver queries при
   наличии, first divergence stmt/call и capability labels.
4. Разделить wall time на startup/frontend, generation, symbolic и replay.

**Tests:** deterministic fake clock, JSON backward compatibility, one fixture
на каждый terminal outcome, round-trip report.

**Acceptance:**

- `unknown reason = 0` на всех 117 broad residual targets;
- 100% targets имеют terminal outcome или explicit `not_started`;
- 100% replay failures имеют first-divergence либо
  `divergence_not_observable`;
- telemetry overhead ≤5% median wall time на primitive baseline.

### WP-ORCH-PROGRESS — progress-based stop в core

**Agent:** `A-ORCH-P`.

**Владение файлами:** новый generic stop strategy в
`usvm-core/.../stopstrategies/` и его core tests. `TsMachine.kt` не менять.

**Задачи:**

1. Generic strategy получает monotonic injectable clock и progress counter.
2. Останавливает машину, если terminal target не удалялся один
   `progressTimeout`.
3. `N × timeout` остаётся только global safety ceiling.
4. Stop reason различает no-progress и global timeout.

**Tests:** fake-clock tests без `sleep`: progress продлевает окно, отсутствие
progress завершает ровно на границе, clock regression невозможен, empty target
list сохраняет старое поведение.

**Acceptance:** после последнего terminal progress ни один batch не работает
дольше `progressTimeout + 10%/100 ms`, при этом legacy strategy не меняется без
feature flag.

**Flag/rollback:** `symbolicProgressStop=false` по умолчанию до ablation;
откат — один core commit.

### WP-ORCH-SHARDS — небольшие batches и replay-pruning

**Agent:** `A-ORCH-S`.

**Dependencies:** WP-TEL, WP-ORCH-PROGRESS.

**Владение файлами:** `hybrid/SymbolicPhase.kt`, новый
`hybrid/TargetShardPlanner.kt`, их tests. Не менять `Main.kt` и report schema.

**Задачи:**

1. Детерминированные shard sizes 1, 4, 8 и all.
2. Начальная группировка по method и CFG region/dominator proximity.
3. После каждого witness немедленный EtsIR replay.
4. Перед следующим shard удалить все попутно покрытые residual edges.
5. Не считать skipped/pruned edge machine run'ом.
6. Сохранить независимые target roots и edge chain
   `entry → if → successor`.

**Acceptance на primitive subset:**

- coverage loss относительно старого per-target baseline ≤1 branch;
- paired median `symbolicTime_new / symbolicTime_legacy ≤ 1.00`, а upper 95%
  bootstrap bound ≤1.10 на заново измеренном WP-BASE legacy baseline;
- paired machine runs меньше legacy; исторические `39.498 s` и `136` служат
  только ориентиром и не являются gate;
- ExpoSE → USVM gain **≥39**;
- broad internal PBT → USVM gain **≥4**;
- нет `N × timeout` tail после последнего progress.

**Flags/rollback:** `targetShardSize=1|4|8|all`,
`replayPruneBetweenShards=true|false`; legacy monolithic batch остаётся режимом
сравнения.

### WP-ORCH-PRUNE — parity target pruning между JVM и TS

**Agent:** `A-ORCH-F`.

**Dependencies:** WP-TEL.

**Владение файлами:** `usvm-ts/.../TsMachine.kt`, новый TS distance/pruning
adapter и TS-specific tests. Не менять `SymbolicPhase.kt`.

**Задачи:**

1. Перенести JVM-паттерн `MultiTargetDistanceCalculator` +
   `TargetsReachableForkBlackList` в `TsMachine` под flag.
2. Использовать реальные active targets состояния, а не исходный список.
3. Проверить cache invalidation после terminal removal.
4. Не вырезать fork, если target location отсутствует или расстояние нельзя
   доказать бесконечным.
5. Отдельно профилировать O(states × targets), не маскируя его pruning'ом.

**Критический regression test:** состояние находится на текущем `if`; один
successor ведёт к target, другой доказуемо не ведёт. Нужный fork остаётся,
ненужный blacklisted. Loop/back-edge и multiple-root fixtures обязательны.

**Acceptance:**

- 0 target losses на synthetic reachability suite;
- replay-confirmed coverage loss ≤1 на primitive campaign;
- число explored states или steps не увеличивается более чем на 5% median;
- хотя бы 20% reduction states/steps на fixture с недостижимыми forks;
- все rejected forks имеют telemetry reason `unreachable_pruned`.

**Flag/rollback:** `tsTargetReachabilityPruning=false` до gate; отключение flag
полностью восстанавливает прежнюю fork policy.

### WP-MAP — source/EtsIR mapping

**Agent:** `A-MAP`.

**Владение в USVM worktree:**

- новые mapping classes и tests;
- `benchmarks/source-targets.cjs`.

**Владение в отдельном jacodb worktree:** только ts-frontend origin sidecar и
его tests. Изменения jacodb идут отдельным commit/PR и не смешиваются с USVM.

**Задачи:**

1. Стабильный source callable ID; текущий broad denominator содержит только
   реально отобранные free/static source entry points.
2. Source span → emitted JS span → EtsIR stmt/edge sidecar.
3. Сохранить module/import/file-init origin и callable binding.
4. Писать `successorStmtIndex`, `successorOrdinal` и mapping enum из §5.
5. Mapping confidence никогда не повышать эвристически без записи ambiguity.

**Acceptance:**

- golden fixtures: 100% exact mapping для `if/else`, loops, ternary,
  short-circuit и optional chaining либо явный `oneToMany`;
- broad denominator остаётся 84/422, primitive — 42/236, если source revision
  не менялась;
- 0 silent drops; все ambiguities присутствуют в mapping report;
- mapping artifact проходит общий contract validator.

### WP-CAP-SCAN — статическая capability classification

**Agent:** `A-CAP`.

**Dependencies:** WP-CONTRACT, WP-MAP, WP-TEL.

**Владение файлами:** новый пакет `usvm-ts-pbt/.../capability/**` и tests.

**Задачи:**

1. Для каждого target вычислить обязательный CFG prefix/conservative slice.
2. Выдать labels: primitive arithmetic, module/init, callable, iterator,
   array/object, map/set, builtin `.call`, spread/yield, unresolved pointer call.
3. Статус: `supported`, `supported_with_flag`, `external_only`, `unsupported`
   или `needs_dynamic_probe`.
4. Статическая неопределённость становится `needs_dynamic_probe`, а не
   автоматически `unsupported`.

**Acceptance:** 100% residual targets broad campaign имеют хотя бы один label,
ручная выборка 50 targets имеет precision/recall не ниже 95% для unsupported
prefix; classifier детерминирован по manifest hash. `unknown = 0` проверяется
только после того, как единый replay/dynamic probe закрыл каждый
`needs_dynamic_probe` terminal outcome.

### Semantic spec layer без общего runtime module

Четыре следующих агента не создают общий production runtime и не зависят друг
от друга. Их результат — dependency-neutral executable fixtures: source input,
Node expected result/trace, EtsIR origin IDs и ожидаемый capability/outcome.
После заморозки fixtures `A-SEM-CONCRETE` и `A-SEM-SYMBOLIC` независимо
реализуют один контракт в своих существующих слоях. Решение о возможном общем
модуле принимается только после обеих реализаций; circular dependency между
concrete и symbolic packages запрещена.

### WP-SEM-MODULE — module init и imports

**Agent:** `A-SEM-MOD`.

**Dependencies:** dirty baseline protected.

**Владение файлами:** dependency-neutral spec и fixtures в отдельном
module-specific подкаталоге тестовых ресурсов. Интеграционные interpreter
files и production `runtime/**` не менять.

**Задачи:** моделировать file-init order, namespace/default/named imports,
cross-file binding и `util.defaultEquals` как реальный callable export.

**Acceptance:** module contract fixtures совпадают с Node.js; ни один binding
не превращается в `undefined` без explicit absent export; подготовлены все 11
ранее падавших namespace/callable witnesses.

### WP-SEM-CALLABLE — function values и dispatch

**Agent:** `A-SEM-CALL`.

**Dependencies:** dirty baseline protected, WP-CONTRACT и WP-MAP.

**Владение файлами:** dependency-neutral callable spec/fixtures. Production
runtime module до архитектурного решения не создавать.

**Задачи:** callable references в ETC, imported/top-level arrow functions,
functions in fields, receiver binding, `.call`, recursion и callback arity.

**Acceptance:** Node ↔ model contract совпадает на direct/field/imported/`.call`
fixtures; все callable ETC cases либо materialize, либо получают точный reject;
никакого unconstrained function-to-undefined fallback.

### WP-SEM-ITERATOR — iterator protocol

**Agent:** `A-SEM-ITER`.

**Dependencies:** dirty baseline protected.

**Владение файлами:** dependency-neutral iterator spec/fixtures. Production
runtime module до архитектурного решения не создавать.

**Задачи:** `Symbol.iterator`, array/string/map/set iterators, `next()` result,
`for-of`, iterator closing для поддерживаемого subset; `yield` классифицировать
отдельно и не выдавать за готовый iterator.

**Acceptance:** 25/25 прежних `Unsupported Symbol.iterator` исчезают на
collection fixture; 9 broad reached-not-replayed iterator targets получают
replay status, из них несовместимые объяснены capability label; Node differential
suite зелёный.

### WP-SEM-BUILTINS — exact builtins, membership и Map

**Agent:** `A-SEM-BLT`.

**Dependencies:** dirty baseline protected.

**Владение файлами:** dependency-neutral builtin spec/fixtures. Production
runtime module до архитектурного решения не создавать.

**Задачи:** exact subset для `Array.isArray`,
`Object.prototype.toString.call`, `hasOwnProperty.call`, property membership,
`Map.get/has/set`, missing-vs-undefined и JS truthiness результата.

**Acceptance:** 2 static-runtime и 1 Map residual blockers replay-confirmed либо
получают semantic mismatch с точным stmt; own/inherited/missing/undefined keys и
`NaN` map key совпадают с Node.

### WP-SEM-CONCRETE — интеграция semantic models в EtsIR replay

**Agent:** `A-SEM-CONCRETE`.

**Dependencies:** WP-SEM-MODULE/CALLABLE/ITERATOR/BUILTINS и защищённый baseline.

**Единоличное владение после baseline:**

- `interpreter/Intrinsics.kt`;
- `interpreter/EtsConcreteInterpreter.kt`;
- `interpreter/CallResolver.kt`;
- `interpreter/VValue.kt`;
- concrete/differential tests.

Агент только подключает подготовленные модели; он не меняет symbolic engine.

**Acceptance:** все concrete tests, ETC tests и Node differential tests зелёные;
500/500 defaultEquals throws и 25/25 iterator Unsupported не воспроизводятся;
существующий coverage не уменьшается.

### WP-SEM-SYMBOLIC — symbolic parity semantic models

**Agent:** `A-SEM-SYMBOLIC`.

**Dependencies:** те же четыре semantic packages и защищённый baseline.

**Единоличное владение после baseline:**

- `usvm-ts/.../interpreter/TsInterpreter.kt`;
- `usvm-ts/.../expr/TsExprResolver.kt`;
- `usvm-ts/.../expr/CallApproximations.kt`;
- новые symbolic model adapters и tests.

**Задачи:** реализовать те же module/callable/iterator/builtin contracts без
unconstrained mock там, где exact model уже определена. Каждый оставшийся mock
получает telemetry label.

**Fidelity gates:**

1. промежуточный gate: закрыты не менее 10 из 14 исходных
   `typescript-collections` reached outcomes;
2. correctness gate: **14/14 закрыты** одним из terminal outcomes:
   `replay_confirmed`, `proved_infeasible`, `exact_unsupported` или
   `exact_capability_mismatch`; witness не обязателен для честно доказанного
   infeasible/unsupported случая;
3. primitive confirmed fidelity остаётся не хуже **144/147** на supported
   paths;
4. ни один новый exact model не даёт Node differential mismatch.

Coverage collections **>14/48** остаётся исследовательской целью и показателем
полезности, но не correctness blocker: unsupported edges остаются uncovered на
broad denominator.

Каждая feature group имеет отдельный flag:
`moduleRuntimeModel`, `callableValueModel`, `iteratorModel`,
`exactCollectionBuiltins`. Отключение возвращает прежнюю approximation.

### WP-ROUTER — capability-aware orchestration

**Agent:** `A-ROUTER`.

**Dependencies:** WP-CAP-SCAN, orchestration packages, fidelity gate.

**Владение файлами:** новый `hybrid/CapabilityRouter.kt`, router tests и новая
configuration data class. `Main.kt` меняет только integration agent.

**Задачи:**

- supported target → Yices shard;
- supported-with-flag → shard только при активной модели;
- external-only → соответствующий adapter queue;
- needs-dynamic-probe → дешёвый bounded probe через единый replay/runtime,
  затем terminal capability status;
- unsupported → report без Yices budget;
- при runtime discovery обновлять reason, но не менять frozen denominator.

**Acceptance:** 100% residual targets routed; статически incompatible targets
потребляют **0 Yices steps и 0 symbolic milliseconds**; coverage supported
targets не ниже режима без routing; false-negative sample = 0 на ручной
валидированной выборке. Unsupported broad edges остаются в broad denominator и
uncovered; исключение возможно только в frozen named common subset §9.

**Flag/rollback:** `capabilityRouting=false` до полной ablation.

### WP-FASTCHECK — стабилизация fast-check adapter

**Agent:** `A-EXT-FC`.

**Dependencies:** WP-CONTRACT, WP-REPLAY, WP-MAP.

**Владение файлов:** только `external-tools/fast-check-adapter/**`.

**Задачи:** structured arrays/objects/receiver plans, единый CLI/raw artifacts,
seed/path replay и shrinking metadata. ETC из USVM/PBT является набором
`examples` и обязательным replay prefix; fast-check не объявляется mutational
engine и эти examples нельзя называть mutation seeds.

**Acceptance:** special-value golden corpus проходит без потерь; fixed
`seed/path` воспроизводим; старые **213/236** primitive coverage не падают более
чем на 1 branch; каждый generated/rejected/shrunk case учтён.

### WP-JAZZER — стабилизация Jazzer.js adapter

**Agent:** `A-EXT-JZ`.

**Dependencies:** WP-CONTRACT, WP-REPLAY, WP-MAP.

**Владение файлов:** только `external-tools/jazzer-adapter/**`.

**Задачи:** typed decoder для ETC v2, receiver/object plans, единый
CLI/artifacts, coverage-increasing и crash corpora, bounded logs, импорт
PBT/USVM ETC как seed corpus.

**Acceptance:** fixed byte corpus воспроизводит те же ETC cases; старые
**211/236** primitive coverage не падают более чем на 1 branch; каждый corpus
entry replay-attempted, а timeout/crash сохраняет partial corpus и metadata.

### WP-SYNTEST — SynTest-JavaScript / DynaMOSA adapter

**Agent:** `A-EXT-SYN`.

**Dependencies:** WP-CONTRACT, WP-REPLAY, WP-MAP.

**Владение файлов:** новый
`external-tools/syntest-adapter/**`; upstream fork — отдельный repository.

**Задачи:** автоматическая генерация harness из manifest, branch/objective
export, concrete test extraction в ETC, native coverage diagnostics, initial
corpus при поддержке upstream. Использовать DynaMOSA как ортогональный
search-based baseline.

**Acceptance:** все 42 primitive source-callable methods классифицированы как
`eligible` или с точной причиной ineligible; 100% eligible attempted и 100%
exported tests переданы единому Kotlin replay. Report показывает полный funnel
`42 → eligible → harnessed → raw cases → replayed → confirmed`, а coverage
среди eligible никогда не выдаётся за coverage 42/236. Fixture mapping exact,
timeout сохраняет partial corpus; Apache-2 `LICENSE/NOTICE` сохранены после
upstream audit.

### WP-NODEMEDIC — NodeMedic-FINE adapter

**Agent:** `A-EXT-NM`.

**Dependencies:** WP-CONTRACT structured v2, WP-REPLAY, WP-MAP.

**Владение файлов:** новый
`external-tools/nodemedic-adapter/**`; upstream fork — отдельно.

**Задачи:** type/object-guided generation для arrays, objects и callbacks в
текущих **84 broad free/static source entry points**; raw ETC export и
capability report на неподдерживаемые signatures.

**Acceptance:** все 84 классифицированы, все declared eligible attempted, 100%
raw cases переданы единому replay; report показывает полный denominator
breakdown, а MIT notice сохранён после audit. Поддержка instance methods не
является текущим gate. Отдельный будущий denominator
`extended-entrypoints-v1` с receiver/constructor plans создаётся только после
появления mapping/harness и затем замораживается до измерений.

### WP-EXPOSE — направленный ExpoSE adapter/fork

**Agent:** `A-EXT-EXP`.

**Dependencies:** WP-CONTRACT, WP-REPLAY, WP-MAP.

**Владение файлов:** только `external-tools/expose-adapter/**`; fork ExpoSE —
в отдельном repo.

**Задачи:** стабилизировать structured inputs, экспорт path constraints/models,
поддержать initial ETC как **concrete path examples**; затем добавить directed source branch IDs или
source-distance, если Jalangi IDs стабильны. Каждый path model проходит Node и
EtsIR replay.

Structured concrete seed support и symbolic heap support — разные capability:
первое не доказывает второе. Object/array case может быть реплеен как concrete
prefix, пока symbolic heap остаётся `unsupported` или `needs_dynamic_probe`.

**Acceptance:** текущие **162/236** standalone и residual fidelity **≥33/34**
не ухудшаются больше чем на 1 branch; нет silent path errors; end-to-end time
включает startup и Z3; fork patch отделён от adapter commit.

### WP-GILLIAN — Gillian-JS model export

**Agent:** `A-EXT-GIL`.

**Dependencies:** WP-CONTRACT, WP-REPLAY, WP-MAP и изолированное
OCaml/opam/Dune окружение.

**Владение файлов:** `external-tools/gillian-adapter/**`; upstream fork —
отдельно.

**Задачи:**

1. ES5/common-subset transpilation и feasibility classification.
2. Предпочтительно экспорт concrete substitution успешного path.
3. Если model export недоступен — assertion-per-target fallback с отдельным
   concrete witness extraction; raw assertion reach всё равно не coverage.
4. Не использовать 116/589 full-manifest feasibility как coverage gate.

**Acceptance:** gate выполняется только на frozen source-callable primitive
common denominator из §9: каждый edge/method получает machine-readable outcome,
exported models проходят ETC validator и единый replay. Full-manifest 116/589
остаётся отдельной feasibility-диагностикой без coverage claim; BSD-3 notice
сохранён после upstream audit. Индивидуальные `model_extraction_failed`, timeout
или отсутствие witness не удаляют edge из denominator и считаются uncovered.

### WP-LEGACY — JSVerify и testcheck-js, только decision gate

**Agent:** не назначать до решения после основной adapter campaign.

JSVerify и testcheck-js не исключаются, но не получают implementation budget,
пока fast-check не исчерпан как PBT baseline. Подключение разрешается только
если дешёвый 20-method pilot показывает хотя бы одно из условий:

- ≥2 новых replay-confirmed edges против fast-check при том же budget;
- статистически заметно иной special-value/object distribution;
- полезный shrink/corpus artifact, отсутствующий у fast-check.

Иначе в финальном отчёте остаётся запись `decision-gated legacy baseline` без
fork и поддержки.

### WP-FUZZILLI — отложено

Fuzzilli генерирует JS-программы и оптимизирован под fuzzing JS engines, а не
typed inputs отдельных функций. Он **deferred** до отдельного проекта по
differential fuzzing frontend/interpreter. В текущую coverage matrix не входит
и не должен отвлекать implementation slots.

### WP-INTEGRATION — единственная точка общих CLI/config изменений

**Agent:** `A-INT`.

**Dependencies:** WP-CONTRACT, WP-REPLAY и все принимаемые core,
orchestration, semantic и adapter commits.

**Единоличное владение:** `report/Main.kt`, top-level Gradle wiring,
`benchmarks/README.md`, новый unified campaign launcher. Агент не переписывает
реализацию пакетов.

**Задачи:** подключить flags, contract validator и run directories; обеспечить
default legacy behavior; записывать все flags в `run-config.json` и report.

**Acceptance:** полный `:usvm-ts-pbt:test`, TS module tests и все Node adapter
tests зелёные; запуск без новых flags schema-compatible читает старый ETC/report
через v1→v2 converter; каждый flag можно выключить независимо.

### WP-CAMPAIGN — ablations, non-inferiority и итоговый отчёт

**Agent:** `A-BENCH`.

**Dependencies:** WP-INTEGRATION и все gates.

**Владение файлов:** новые campaign/summarizer scripts и
`benchmarks/results/<campaign-id>/**`. Product code и docs не менять.

Подробный протокол — в §8–§10.

### WP-DOC — независимая аналитическая сборка

**Agent:** `A-DOC`.

**Dependencies:** immutable outputs WP-BENCH и handoff reports всех agents.

**Владение:** final analytical report и обновления этого roadmap. Product code,
raw artifacts и summarizers не менять.

**Acceptance:** все таблицы пересчитываются из compact machine-readable
summary; denominators/flags/commits/audit rows указаны; historical и fresh
timings не смешаны; conclusions различают correctness, performance и research
targets.

## 8. Ablation matrix

### 8.1. Orchestration ablation

На одних residual sets и corpus snapshots запустить:

1. старый per-target;
2. monolithic list batch;
3. batch + progress stop;
4. shards 4 + progress stop;
5. shards 8 + progress stop;
6. лучший shard + TS fork pruning;
7. предыдущий режим + replay-pruning;
8. предыдущий режим + capability routing.

Для каждого: coverage, targets, reached, extracted, replay-confirmed, runs,
states, steps, time-to-first/last target, time after last progress, stop reasons.
Нельзя одновременно включить две новые оптимизации без строк их отдельных
абляций.

### 8.2. Semantic ablation

Поверх лучшего orchestration режима:

1. baseline semantics;
2. +concrete/symbolic module-contract implementations;
3. +concrete/symbolic callable-contract implementations;
4. +concrete/symbolic iterator-contract implementations;
5. +concrete/symbolic builtin-contract implementations;
6. все модели;
7. все модели + capability router.

Основная метрика здесь — reached→replay conversion и concrete differential
correctness, а не только время.

### 8.3. Tool matrix

Standalone:

- internal PBT;
- fast-check;
- Jazzer.js;
- SynTest-JavaScript/DynaMOSA;
- NodeMedic-FINE;
- ExpoSE;
- Gillian-JS на зафиксированном common subset;
- USVM.

Hybrids:

- каждый внешний producer → EtsIR replay → USVM;
- internal PBT → USVM;
- pairwise orthogonal ensembles → USVM;
- all-compatible ensemble → USVM;
- USVM witnesses → Jazzer mutation seeds как отдельная строка;
- USVM/PBT ETC → fast-check examples/replay prefix как отдельная, не
  mutational, строка.

JSVerify/testcheck-js появляются только после WP-LEGACY gate. Fuzzilli в этой
матрице отсутствует.

## 9. Benchmark protocol

### 9.1. Заранее именованные denominators

1. **`D_broad-v1`:** текущие 84 free/static source entry points / 422 edges.
   Это показатель реальной применимости. Любой unsupported edge остаётся в
   denominator и считается uncovered.
2. **`D_primitive-reference-v1`:** текущие 42 source-callable primitive entry
   points / 236 edges. Это полный reference funnel, но не автоматически common
   subset каждого нового tool.
3. **`D_symbolic-primitive-common-v1`:** одно пересечение exact-mapped
   source-callable primitive edges, для которых **до run** статически
   подтверждены capability class, harness/transpilation support и заявленная
   инструментом возможность model export/assertion-witness export. Оно
   выводится из `D_primitive-reference-v1` и фиксируется до основной coverage
   campaign без проверки успешности model extraction на конкретном edge. Один
   и тот же frozen список используется в обеих hypotheses §10.
4. **`D_structured-concrete-common-v1`:** отдельное пересечение для concrete
   producers fast-check, Jazzer, SynTest, NodeMedic и ExpoSE concrete examples.
   Оно может включать arrays/objects, но не означает symbolic heap support.
5. **`D_extended-entrypoints-v1`:** будущий denominator instance methods с
   receiver/constructor plans. Он создаётся после реализации mapping/harness и
   не подменяет текущие 84 free/static entry points.

Для каждого named denominator публикуются manifest hash, method/edge list,
numerator, denominator, support rate к `D_broad-v1` и полный excluded-reason
breakdown. Exclusion после просмотра run outcome запрещён.
`model_extraction_failed`, timeout, solver error и no witness являются
uncovered outcomes внутри frozen denominator. Исключить можно только целый,
заранее описанный feature class, если pre-run capability audit показывает, что
для него инструмент в принципе не экспортирует models/witnesses; class и reason
фиксируются до campaign, не по результатам отдельных edges.

### 9.2. Runs, budgets и deadline

- минимум **10 seeds** для каждой stochastic configuration;
- deterministic engines также получают минимум **10 повторных timed runs** в
  каждом cold/warm режиме; coverage ожидается одинаковым, любое расхождение
  становится stability defect;
- одинаковый seed schedule, method order и named denominator в paired run;
- порядок инструментов/режимов чередуется;
- одинаковый end-to-end budget и одинаковая grace formula §5.4;
- end-to-end включает startup, instrumentation, harness generation,
  exploration, export и incremental replay;
- cold и warm/cache-preserving результаты не смешиваются;
- timeout сохраняет partial corpus, late coverage исключается, `overBudgetMs`
  публикуется;
- frontend, project и tool revisions берутся только из завершённого upstream
  audit WP-BASE.

Для symbolic engines дополнительно публикуются solver/exploration и replay
times, но primary comparison остаётся end-to-end.

### 9.3. Метрики и AUC

Primary:

- replay-confirmed EtsIR branch coverage на named denominator;
- coverage AUC от `t=0` до hard result deadline;
- marginal confirmed edges after fixed predecessor corpus.

AUC — интеграл ступенчатой функции
`confirmed edges with discoveredAtMs ≤ t / |D|`. Case обязан иметь
`generatedAtMs`, впервые подтверждённый edge — `discoveredAtMs`; replay идёт
incrementally в порядке §5.3. Edge, подтверждённый после deadline, остаётся в
diagnostics и не попадает в primary AUC/fixed-budget coverage.

Secondary:

- time-to-target, time-to-last-progress и over-budget;
- reached/extracted/replayed/confirmed funnel;
- states, steps, queries, machine runs;
- unsupported/capability breakdown;
- native-to-EtsIR mapping confidence;
- unique reproducible exceptions;
- startup/generation/export/replay cost.

Paired bootstrap иерархический: resample projects, затем methods внутри
project, затем paired seeds/repetitions внутри method. Показываются median
paired difference и confidence interval, per-project результаты и aggregate.
Плоский bootstrap по всем edges или aggregate без project breakdown запрещён.

## 10. Две заранее заданные non-inferiority hypotheses

Post-hoc comparator `max(ExpoSE, Gillian)` запрещён. На одном frozen
`D_symbolic-primitive-common-v1` заранее проверяются две гипотезы при одинаковом
end-to-end budget:

```text
delta_E = coverage(usvm-ts) - coverage(ExpoSE)
H0_E: delta_E <= -0.02       H1_E: delta_E > -0.02

delta_G = coverage(usvm-ts) - coverage(Gillian-JS)
H0_G: delta_G <= -0.02       H1_G: delta_G > -0.02
```

Обе проверки односторонние и paired по project/method/seed. Для family-wise
`alpha=0.05` применяем Holm correction к двум заранее зарегистрированным
p-values: меньший сравнивается с `0.025`, второй — с `0.05`. Общий вывод
«USVM non-inferior внешним symbolic engines» разрешён только при отклонении
обоих H0. Одновременно публикуются hierarchical paired bootstrap intervals.

Margin 2 pp замораживается до campaign. Его практический смысл на текущих
236 reference edges — 4.72 edge, то есть примерно пять ветвей, но решение не
делается по округлённому edge count или показанному проценту: используются
точные fractions и bootstrap distribution на фактическом frozen denominator.

Обязательные условия:

- один source/ETC/EtsIR mapping и один `D_symbolic-primitive-common-v1` для
  обеих hypotheses;
- одинаковые cold/warm режимы, deadlines и Kotlin replay;
- broad unsupported edges остаются uncovered и показываются отдельно;
- индивидуальный Gillian/ExpoSE/USVM timeout, `model_extraction_failed` или no
  witness остаётся uncovered внутри общего denominator и не меняет его;
- capability-wide исключение Gillian допустимо только до campaign для заранее
  названного feature class, который согласно static audit вообще не имеет
  model/assertion-witness export. Если после такого pre-run audit невозможно
  сформировать один общий denominator, H_G помечается `not_testable`, а общий
  family-level claim не делается;
- raw paths, SAT count и native claimed coverage не являются заменой primary
  metric.

Если hypothesis не проходит, отчёт даёт exact per-feature gap и следующий
semantic work package; comparator после результата не заменяется.

## 11. Порядок волн и checkpoints

### Wave 0 — зафиксировать измеримость

После `A-BASE`: `A-CONTRACT` замораживает v2, `A-REPLAY` делает replay на
fixtures; параллельно по свободным слотам идут `A-MAP`, `A-TEL`, `A-ORCH-P`.
Ни один adapter agent до принятия `A-CONTRACT + A-REPLAY + A-MAP` не стартует.

Checkpoint W0:

- schemaVersion 2 frozen, v1 converter и validator зелёные;
- raw adapter fixture → единый Kotlin replay воспроизводим;
- после dynamic probes terminal capability unknown = 0;
- progress strategy unit-tested;
- dirty semantic files защищены и назначены владельцам.

### Wave 1 — orchestration и semantic contracts

Параллельно тройками:

- `A-ORCH-S`, `A-ORCH-F`, `A-CAP`;
- затем `A-SEM-MOD`, `A-SEM-CALL`, `A-SEM-ITER`;
- затем `A-SEM-BLT`, `A-SEM-CONCRETE`, `A-SEM-SYMBOLIC` по зависимостям.

Checkpoint W1:

- orchestration gates из WP-ORCH-S выполнены;
- TS pruning выключаем feature flag'ом без изменения результата;
- collections ≥10/14 закрытых outcomes, затем 14/14 закрытых outcomes;
- incompatible targets не потребляют Yices после включения router.

### Wave 2 — adapters

Параллельно первой тройкой: `A-EXT-FC`, `A-EXT-JZ`, `A-EXT-SYN`.

Второй тройкой: `A-EXT-NM`, `A-EXT-EXP`, `A-EXT-GIL`.

Третьей волной: `A-ROUTER` и затем integration follow-up после освобождения
слотов.

Checkpoint W2:

- каждый adapter выдаёт только raw corpus/native/meta, проходит validator, а
  replay-report/residual создаёт только `A-REPLAY`;
- partial timeout не теряет corpus;
- хотя бы primitive common run доступен для каждого применимого tool;
- licenses/notices сохранены.

### Wave 3 — интеграция и ablations

`A-INT` последовательно интегрирует только принятые commits. Затем `A-BENCH`
запускает orchestration и semantic ablations. Product fixes во время campaign
запрещены: найденный defect получает issue/package для следующей итерации.

Checkpoint W3:

- полный test suite зелёный;
- legacy flags воспроизводят baseline;
- acceptance gates не вычисляются из разных denominators;
- raw artifacts и compact summary сохранены.

### Wave 4 — многосидовая кампания и отчёт

`A-BENCH` выполняет ≥10 seeds/repetitions, cold/warm runs, hierarchical paired
bootstrap и две Holm-corrected hypotheses. Затем `A-DOC` выпускает report:

- standalone и hybrid таблицы;
- broad, primitive и structured common denominators;
- orchestration/semantic ablations;
- reached→replay funnel;
- feature-gap и mapping-confidence breakdown;
- end-to-end cost и coverage AUC;
- точный вывод о non-inferiority, без подмены raw path counts.

## 12. Definition of done всей программы

Работа считается завершённой только при одновременном выполнении:

1. orchestration: paired median time ratio к заново измеренному legacy ≤1.00,
   upper 95% bound ≤1.10, runs меньше paired legacy, coverage loss ≤1,
   Expo gain ≥39, broad PBT gain ≥4; `39.498 s/136` только historical reference;
2. stopping: нет `N × timeout` после последнего progress;
3. fidelity correctness: 14/14 исходных outcomes закрыты confirmed либо exact
   infeasible/unsupported/capability result; `>14/48` только research target;
4. routing: после dynamic probe 100% residual targets имеют terminal status,
   incompatible targets тратят 0 Yices, broad unsupported остаётся uncovered;
5. adapters: schema v2 raw contract, единый Kotlin replay и no silent drops;
6. comparison: ≥10 seeds/repetitions, deadline+grace accounting, cold/warm и
   hierarchical paired CI/AUC timestamps;
7. non-inferiority: обе заранее заданные hypotheses проверены на одном frozen
   denominator с Holm correction либо общий claim явно не сделан;
8. safety: текущие пользовательские dirty files не потеряны и не попали в
   чужие commits; каждое изменение откатывается отдельным commit/feature flag.

Главный практический приоритет: сначала telemetry + progress stop + shards,
параллельно module/callable/iterator/builtin fidelity. Добавление новых tools
имеет смысл только через общий ETC/mapping/replay contract; иначе появятся ещё
несопоставимые проценты, а не ответ, где именно USVM выигрывает или проигрывает.
