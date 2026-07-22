# Продолжение работы над USVM + PBT для TypeScript

> Актуально на 2026-07-22. Это основной handoff-документ для новой рабочей
> машины и новой агентской сессии. Предыдущий локальный контекст не нужен:
> необходимый код, тесты, baseline, схемы artifacts и документы находятся в
> ветке `caelmbleidd/ts-pbt-roadmap-integration`.

## 1. Цель и текущая отправная точка

Исследовательская цель — улучшить результаты USVM на реальных TypeScript-
проектах и подготовить воспроизводимую статью примерно на тему
«Комбинирование символьного исполнения и property-based testing для
TypeScript». Основной symbolic engine — USVM. PBT-движок может меняться;
сторонние symbolic/fuzzing-инструменты используются как сравнение при едином
контракте artifacts и одинаковых знаменателях покрытия.

Активная ветка:

```text
caelmbleidd/ts-pbt-roadmap-integration
```

Последний implementation checkpoint перед handoff-документом:

```text
5de4e2efe0c0bbe5e4c75fe58848aae5bc0ba9e4
Add SynTest DynaMOSA artifact adapter
```

Локальный dirty-worktree старой ветки `caelmbleidd/ts_pbt` для продолжения не
нужен и не должен накладываться поверх integration-ветки. Его релевантные
идеи уже интегрированы; каталог реальных проблем перенесён в этот commit.

## 2. Клонирование ветки на новой машине

Проверьте доступ к GitHub и клонируйте именно integration-ветку:

```bash
mkdir -p "$HOME/Programming"
cd "$HOME/Programming"

git clone --branch caelmbleidd/ts-pbt-roadmap-integration --single-branch \
  git@github.com:UnitTestBot/usvm.git usvm-ts-pbt
cd usvm-ts-pbt

git status --short --branch
git log --oneline --decorate -25
```

HTTPS-вариант:

```bash
git clone --branch caelmbleidd/ts-pbt-roadmap-integration --single-branch \
  https://github.com/UnitTestBot/usvm.git usvm-ts-pbt
```

Рекомендуется не добавлять upstream к `origin/main` для локальных
исследовательских веток. Если создаётся отдельная ветка для следующей волны:

```bash
git switch -c caelmbleidd/ts-pbt-next
git branch --unset-upstream 2>/dev/null || true
```

## 3. Требуемое окружение

### 3.1 Базовые инструменты

- Git;
- JDK 21 для тестов и detekt;
- JDK 23.0.2 для точного воспроизведения frozen benchmark;
- Node.js 22 и npm для frontend и adapters;
- `jq` для benchmark-скриптов.

Проверенная локальная комбинация для полного Gradle test run: Corretto
21.0.6, Node.js 18.20.8, npm 10.8.2. Node.js 22 предпочтителен, потому что
его использует TypeScript CI. Старый ExpoSE baseline зафиксирован отдельно на
Node.js 21.7.2.

Gradle устанавливать не нужно — используйте `./gradlew` из репозитория.
Yices отдельно устанавливать также не нужно: solver поставляется через KSMT.

### 3.2 jacodb TypeScript frontend

Для точного benchmark checkpoint используется jacodb commit
`ed94d48c78bd69464b6f2ef7f9635cd93a6bd66d`:

```bash
cd "$HOME/Programming"
git clone https://github.com/UnitTestBot/jacodb.git jacodb-ts-pbt
cd jacodb-ts-pbt
git checkout ed94d48c78bd69464b6f2ef7f9635cd93a6bd66d

cd jacodb-ets/ts-frontend
npm ci || npm install
npm run build

export JACODB_DIR="$HOME/Programming/jacodb-ts-pbt"
export ETS_IR_PROVIDER=ts-frontend
export ETS_FRONTEND_DIR="$JACODB_DIR/jacodb-ets/ts-frontend"
test -f "$ETS_FRONTEND_DIR/dist/index.js"
```

Для повседневной разработки допустим jacodb `ba042500` или новее, но
сравнение с frozen baseline следует проводить на `ed94d48c...`.

### 3.3 ArkAnalyzer

Нужна именно ветка `neo/2025-09-03`, закреплённая в CI. На других ветках
менялся порядок successors у `if`; это способно незаметно инвертировать
ветвления после конвертации EtsIR.

```bash
cd "$HOME/Programming"
git clone --depth=1 --branch neo/2025-09-03 \
  https://gitcode.com/Lipen/arkanalyzer arkanalyzer-neo-2025-09-03
cd arkanalyzer-neo-2025-09-03
npm install
npm run build

export ARKANALYZER_DIR="$PWD"
test -f "$ARKANALYZER_DIR/out/src/save/serializeArkIR.js"
```

GitCode иногда отвечает нестабильно; повторный clone допустим. Не заменяйте
закреплённую ветку случайной локальной сборкой ArkAnalyzer.

### 3.4 Удобный локальный env-файл

Можно создать неотслеживаемый файл, например `~/.config/usvm-ts-pbt/env`:

```bash
export JAVA_HOME=/absolute/path/to/jdk-21
export JACODB_DIR="$HOME/Programming/jacodb-ts-pbt"
export ETS_IR_PROVIDER=ts-frontend
export ETS_FRONTEND_DIR="$JACODB_DIR/jacodb-ets/ts-frontend"
export ARKANALYZER_DIR="$HOME/Programming/arkanalyzer-neo-2025-09-03"
```

Не коммитьте машинно-зависимые абсолютные пути.

## 4. Первая сборка и обязательная проверка

Из корня USVM:

```bash
source "$HOME/.config/usvm-ts-pbt/env"  # если файл создан

./gradlew -PuseLocalJacodb="$JACODB_DIR" \
  :usvm-ts-pbt:test --rerun-tasks
```

Ожидается `BUILD SUCCESSFUL`. Последний полный проверенный прогон включал
unit, hybrid E2E, PBT, replay, concrete semantics, concrete-vs-symbolic
differential, capability, artifact-contract, telemetry и semantic contract
tests и завершился успешно.

Флаг `--rerun-tasks` здесь важен: изменение `ARKANALYZER_DIR`,
`ETS_IR_PROVIDER` или `ETS_FRONTEND_DIR` не инвалидирует Gradle test cache.

Дополнительная проверка форматирования/статического анализа выполняется на
JDK 21:

```bash
./gradlew -PuseLocalJacodb="$JACODB_DIR" :usvm-ts-pbt:check
```

## 5. Проверка frozen baseline и внешних adapters

Baseline является частью ветки и не требует повторного длительного запуска:

```bash
node usvm-ts-pbt/benchmarks/baselines/2026-07-19/scripts/validate-baseline.mjs
```

Ожидаемые контрольные числа:

| Denominator / mode | Покрытые рёбра |
|---|---:|
| broad denominator | 422 (84 метода) |
| broad internal PBT | 305/422 |
| broad hybrid | 309/422 |
| broad USVM | 240/422 |
| primitive denominator | 236 (42 метода) |
| primitive internal PBT | 217/236 |
| primitive hybrid | 220/236 |
| primitive USVM | 169/236 |
| primitive fast-check | 213/236 |
| primitive Jazzer.js | 211/236 |
| primitive ExpoSE | 162/236 |
| primitive ensemble | 220/236 |

Установите и проверьте adapters независимо:

```bash
cd usvm-ts-pbt/external-tools/fast-check-adapter
npm ci
npm test
npm run check

cd ../jazzer-adapter
npm ci
npm test

cd ../syntest-adapter
npm ci
npm run check
```

На checkpoint были пройдены 8/8 тестов fast-check, 16/16 тестов Jazzer и
16/16 тестов SynTest adapter. SynTest DynaMOSA adapter, audit metadata,
fixtures и runner contract находятся в ветке; реальный длинный SynTest
campaign ещё не запускался и runtime SynTest не вендорится.

## 6. Корпус реальных проектов

Метаданные закреплены в `usvm-ts-pbt/benchmarks/corpus.json`. Загрузить точные
ревизии:

```bash
cd "$HOME/Programming/usvm-ts-pbt/usvm-ts-pbt/benchmarks"
./fetch-corpus.sh
```

Корпус:

- TheAlgorithms/TypeScript
  `19b4ced86c99815f142d4a46a028f55487b8038a`;
- loiane/javascript-datastructures-algorithms
  `e8ee8f9b8a07589533c4243a210d4cea7b090b10`;
- basarat/typescript-collections
  `309bb1b6955b403b212309531607b8d17df152e5`.

Для точного воспроизведения frozen broad benchmark используйте JDK 23.0.2,
jacodb `ed94d48c...`, seed `20260719`, 100 PBT-итераций и timeout 1 s.
Пример для TheAlgorithms `maths`:

```bash
cd "$HOME/Programming/usvm-ts-pbt"
export JAVA_HOME=/absolute/path/to/jdk-23.0.2
export BASELINE="$PWD/usvm-ts-pbt/benchmarks/baselines/2026-07-19"
export CORPUS="$PWD/usvm-ts-pbt/benchmarks/corpus"
export RAW="$PWD/usvm-ts-pbt/benchmarks/results/reproduced"

./gradlew -q -PuseLocalJacodb="$JACODB_DIR" :usvm-ts-pbt:runHybrid \
  --args="$CORPUS/TheAlgorithms-TypeScript/maths --recursive \
  --method-ids $BASELINE/projects/the-algorithms-maths/entry-method-ids.txt \
  --modes PBT_ONLY,HYBRID,SYMBOLIC_ONLY --seed 20260719 \
  --pbt-iterations 100 --target-timeout 1 \
  --out $RAW/the-algorithms-maths/source-entry-internal-100-batched"
```

Точные команды для всех трёх проектов и external corpora находятся в
`usvm-ts-pbt/benchmarks/baselines/2026-07-19/README.md`.

## 7. Что уже интегрировано

От roadmap base `c31b924f` до checkpoint `5de4e2ef` последовательно
интегрированы:

1. progress-based stop strategy и его factory wiring;
2. artifact contract v2 и frozen benchmark baseline;
3. telemetry schema, recorder и summarizer;
4. source-to-EtsIR mapping sidecar и единый replay pipeline;
5. консервативный reachability pruning, targeted progress stopping и
   deterministic sharding;
6. capability scanner и replay provider;
7. semantic contracts/fixtures для builtins, modules, iterators и callable;
8. opt-in symbolic models для collections, callable и iterators;
9. стабилизация raw artifacts fast-check и Jazzer.js;
10. concrete TypeScript semantic replay и differential oracle;
11. SynTest DynaMOSA adapter для общего artifact contract.

Полный commit trail виден командой:

```bash
git log --reverse --oneline c31b924f..5de4e2ef
```

## 8. Измеренный эффект последней semantic-итерации

На реальном `typescript-collections` concrete PBT улучшился с 14/48 до
33/48 методов, `unsupported=0`.

Для comparator family на 500 запусках получено 238 нормальных возвратов и
262 корректных `TypeError` для честно сгенерированных невызываемых значений;
ошибок вида `defaultEquals is undefined` и внутренних `Unsupported` не было.
Для `forEach` на 100 запусках: 42 нормальных возврата и 58 честных
non-callable `TypeError`.

Это не следует смешивать с frozen coverage baseline из секции 5: semantic
измерение проверяет воспроизводимость и отсутствие искусственного
`Unsupported`, а baseline фиксирует общий denominator и сравнение режимов.

## 9. Главные незавершённые задачи

Порядок ниже выбран так, чтобы сначала закрыть fidelity replay, затем
измерять влияние маршрутизации и только после этого расширять сравнение.

### P0: production replay boundary

1. Production path всё ещё использует `StrictContractReplayValueDecoder`.
   Подключить `ConcreteSceneMaterializer` для callable, constructor, alias и
   projected cases.
2. В `ExternalValueCodec.encode` обрабатывать `VNativeFunction` до общей
   ветки `VObject`.
3. Устранить collision static/instance в `stableMethodId` одновременно в
   contract, mapping и consumers.
4. Разрешать constructor plan только при `callableKind=class`.
5. Довести sparse-array holes и aliases через полную границу
   encode/decode/materialize/replay.

Acceptance: новый input проходит путь
`target manifest -> ETC v2 -> replay -> concrete oracle` без эвристической
потери callable-kind, alias identity и sparse shape.

### P1: orchestration / A-INT

1. Завершить router, связывающий capability report с выбором PBT, USVM и
   external tools.
2. Провести multiseed A/B benchmark с одинаковыми budget, denominator и
   artifact validation.
3. Отдельно измерять reached, replay-confirmed, unsupported-prefix,
   wall-clock и marginal edge gain каждого движка.
4. Проверить opt-in flags: `moduleRuntimeModel`, `callableValueModel`,
   `iteratorModel`, `exactCollectionBuiltins`. Сейчас они выключены по
   умолчанию; CLI wiring через `SymbolicPhase` не закончено.

### P2: внешние сравнения

1. Запустить реальный SynTest campaign через готовый adapter.
2. Реализовать вторую волну adapters для NodeMedic, ExpoSE и Gillian либо
   явно зафиксировать неприменимость инструмента по capability/IR contract.
3. Не сравнивать сырое число найденных тестов: переводить результаты в ETC
   v2, делать replay и считать покрытие по общему source denominator.

### P3: статья

1. Обновить таблицы только из валидированных machine-readable artifacts.
2. Разделить frozen baseline и новые multiseed результаты.
3. Описать threat-to-validity: frontend drift, replay rejection,
   unsupported prefix, timeouts, seed variance и различия tool domains.
4. Сохранить отрицательные результаты: replay fidelity и реальные
   TypeScript semantics являются частью научного результата, а не только
   инженерным шумом.

## 10. Правила экспериментальной воспроизводимости

Для каждого нового запуска сохранять:

- commit USVM и внешнего инструмента;
- commit корпуса и список method IDs;
- JDK/Node/npm версии;
- frontend provider и его commit;
- seed, budget, timeout и mode;
- validated artifacts v2, stdout/stderr и exit status;
- denominator ID и source mapping;
- coverage до/после concrete replay.

Не переносить результаты между разными denominators. Не считать
replay-rejected target покрытым. Не интерпретировать `Unsupported` как
обычный exception программы. Перед обвинением текущего diff проверять, не
воспроизводится ли сбой на base branch или другом frontend provider.

## 11. Карта документов и артефактов

- `docs/ts-pbt/00-project-state.md` — исторический state до roadmap-wave;
- `docs/ts-pbt/01-arkanalyzer-and-ets-ir.md` — EtsIR/frontend contract;
- `docs/ts-pbt/02-concrete-interpreter-and-differential-findings.md` —
  concrete oracle и differential findings;
- `docs/ts-pbt/03-hybrid-pipeline.md` — устройство pipeline;
- `docs/ts-pbt/04-jacodb-native-parser-compat.md` — native frontend;
- `docs/ts-pbt/05-real-world-problems.md` — каталог проблем реального кода;
- `docs/ts-pbt/13-agent-implementation-roadmap.md` — полный task graph;
- `docs/ts-pbt/14-continuation-guide.md` — текущий handoff;
- `usvm-ts-pbt/README.md` — модуль и команды;
- `usvm-ts-pbt/artifact-contract/v2/README.md` — контракт artifacts;
- `usvm-ts-pbt/benchmarks/baselines/2026-07-19/README.md` — frozen baseline;
- `usvm-ts-pbt/benchmarks/results/` — сохранённые результаты;
- `usvm-ts-pbt/external-tools/*/README.md` — adapters и tool-specific run.

## 12. Старт новой агентской сессии

После clone и первого test run можно начать новую сессию таким запросом:

```text
Работаем в ветке caelmbleidd/ts-pbt-roadmap-integration проекта USVM.
Сначала полностью прочитай docs/ts-pbt/14-continuation-guide.md, затем
docs/ts-pbt/13-agent-implementation-roadmap.md и релевантные документы из
карты. Проверь git status и текущие тесты. Продолжай с P0 production replay
boundary, делегируя независимые roadmap-задачи сабагентам. Не меняй frozen
baseline задним числом; новые результаты сохраняй отдельным multiseed
снимком и сравнивай по общему denominator после concrete replay.
```

После каждой интеграции обновляйте этот документ фактическими commit hashes,
проверенными командами, измеренным эффектом и оставшимися blockers. Не
останавливайтесь на зелёных unit-тестах: конечный критерий — улучшение или
объяснимое отрицательное наблюдение на закреплённых реальных проектах.
