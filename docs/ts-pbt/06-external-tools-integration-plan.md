# План интеграции внешних PBT, fuzzing и symbolic-execution инструментов

> Статус: M0–M6 реализованы/проверены; M7 имеет первый numeric pilot, M8
> остаётся многопроектной кампанией. Текущий конкретный EtsIR-интерпретатор и
> `usvm-ts` сохраняются. Внешние инструменты добавляются как независимые
> источники входов, покрытия и экспериментальные baseline'ы.

## 1. Цель

Построить не жёстко прошитый двухфазный прототип, а экспериментальную платформу,
в которой несколько генераторов и анализаторов обмениваются одним переносимым
corpus'ом:

```text
TypeScript/JavaScript source
        │
        ├── ts-frontend ──> EtsIR + origin sidecar
        │                         │
        ├── fast-check ───────────┤ concrete inputs
        ├── Jazzer.js ────────────┤ coverage-guided inputs
        ├── ExpoSE ───────────────┤ DSE inputs/traces
        └── Gillian-JS ───────────┤ symbolic inputs/traces (если применим)
                                  ▼
                       External Test Corpus
                                  │
                                  ▼
                   concrete replay на EtsIR
                    ├── нормализованное покрытие
                    ├── type profile
                    ├── differential result
                    └── неподтверждённые входы
                                  │
                                  ▼
                  непокрытые EtsIR edges ──> USVM
```

Ключевой принцип: решение о том, какие цели передавать USVM, принимается по
результату replay входов нашим конкретным EtsIR-интерпретатором. Чужое покрытие
используется как дополнительный сигнал и для диагностики, но не подменяет
EtsIR-покрытие, пока mapping не подтверждён.

## 2. Инструменты-кандидаты

### Генеративное и property-based testing

| Инструмент | Роль | Решение |
|---|---|---|
| [fast-check](https://github.com/dubzzz/fast-check) | Основной JS/TS PBT baseline: arbitraries, seed/replay, shrinking, model-based testing | Интегрировать первым; форк не требуется до появления конкретного ограничения |
| [JSVerify](https://github.com/jsverify/jsverify) | Исторически распространённый QuickCheck-подобный baseline | Подключить через общий corpus adapter; не строить архитектуру вокруг него |
| [testcheck-js](https://github.com/leebyron/testcheck-js) | Clojure.test.check-подобный генератор | Проверить воспроизводимость на примитивных/структурных входах; низкий приоритет |
| `@justanotherdot/hedgehog` | Альтернативная shrink-tree модель | Feasibility после fast-check; полезен как абляция стратегии shrinking |
| Effect Schema + fast-check | Генерация валидных объектов по runtime schema | Использовать на проектах с Effect/Zod/JSON Schema как отдельную конфигурацию |

### Coverage-guided fuzzing

| Инструмент | Роль | Решение |
|---|---|---|
| [Jazzer.js](https://github.com/CodeIntelligenceTesting/jazzer.js) | Основной coverage-guided Node.js fuzzer, libFuzzer-style corpus | Интегрировать вторым; его coverage-increasing corpus особенно хорошо подходит для EtsIR replay |
| [jsfuzz](https://github.com/fuzzitdev/jsfuzz) | Старый coverage-guided JS fuzzer | Только исторический baseline: upstream архивирован |
| [Fuzzilli](https://github.com/googleprojectzero/fuzzilli) | Fuzzer JS-движков, генерирует программы через FuzzIL | Не использовать как основной input fuzzer функций; рассмотреть отдельно для differential-тестирования frontend/interpreter |

### Symbolic/concolic execution

| Инструмент | Роль | Решение |
|---|---|---|
| [ExpoSE](https://github.com/ExpoSEJS/ExpoSE) | Главный внешний DSE baseline; Jalangi2 + Z3, Node/browser, replay | Обязательная интеграция и сравнение с `usvm-ts`; при необходимости форк для branch targets и экспорта corpus |
| [Gillian-JS](https://gillianplatform.github.io/instantiations/js/symbolic-testing.html) / JaVerT | Compositional symbolic execution, JS→GIL | Обязательный feasibility; включить в финальное сравнение, если поддерживаемый ES5/common subset покрывает достаточную долю корпусов |
| [Jalangi/Jalangi2](https://github.com/Samsung/jalangi2) | Инструментация и исторический concolic framework | Использовать как instrumentation substrate и исторический baseline; отдельный современный symbolic engine поверх него уже даёт ExpoSE |
| SymJS, Kudzu, Artemis | Академические JS test-generation системы | Описать в related work; подключать только если доступны воспроизводимая сборка и экспорт конкретных входов |

### Покрытие и source mapping

| Инструмент | Что берём |
|---|---|
| [Istanbul/nyc](https://github.com/istanbuljs/nyc) | `branchMap`, statement/function coverage, TypeScript source-map remapping |
| V8 precise coverage / `c8` | Диапазоны исполняемого JavaScript без переписывания программы |
| TypeScript source maps | JS byte/line ranges → исходные TS ranges |
| ts-frontend origin sidecar | TS ranges → EtsIR statement/edge IDs; этого слоя сейчас не хватает |

## 3. Универсальные форматы обмена

### 3.1 Target Manifest

USVM экспортирует manifest после построения EtsIR. В нём для каждой точки входа:

- стабильный `methodId`: файл + класс/namespace + имя + arity;
- полная EtsIR signature для диагностики;
- source export/class/member, если frontend смог его определить;
- типы параметров в EtsIR и упрощённая JSON-schema-подобная форма;
- признак instance/static/free function;
- список EtsIR branch IDs.

Manifest является входом для генераторов harness'ов fast-check, Jazzer.js и
ExpoSE. Анонимное имя `%AM…` нельзя выдавать внешнему инструменту как единственный
идентификатор: frontend должен сохранить имя исходного export/binding.

### 3.2 External Test Corpus (ETC)

Версионированный JSON/JSONL:

```json
{
  "schemaVersion": 1,
  "producer": "fast-check@4.9.0",
  "cases": [
    {
      "id": "seed-42-run-17",
      "methodId": "src/math.ts::%dflt::factorial/1",
      "receiver": { "kind": "undefined" },
      "arguments": [{ "kind": "number", "value": "42" }],
      "metadata": { "seed": "42", "path": "17" }
    }
  ]
}
```

Wire-формат значений обязан различать `undefined`, `null`, `NaN`, `±Infinity`,
`-0`, array holes, массивы и объекты. В v1 циклические объекты и функции могут
быть отмечены как `unrepresentable`; они не должны молча превращаться в другое
значение.

### 3.3 Origin/Coverage sidecar

Для каждого EtsIR statement и branch edge:

- `methodId`, `stmtIndex`, `successorIndex`, `branchId`;
- исходный файл и `[startOffset, endOffset)` TS-диапазон;
- синтетический/пользовательский origin;
- исходный вид условия (`if`, `while`, `&&`, `?:`, optional chaining и т.д.);
- при наличии — emitted JS range из TypeScript source map;
- при наличии — Istanbul branch/function ID.

Сейчас `EtsStmtLocation` содержит только `(method, index)`, поэтому origin надо
сохранять во время lowering в ts-frontend, а не пытаться угадать после загрузки.
Sidecar предпочтительнее немедленного изменения публичной EtsIR-модели: его
можно прототипировать и валидировать независимо.

## 4. Пайплайны, которые должны поддерживаться

1. `INTERNAL_PBT -> USVM`: текущий baseline.
2. `FAST_CHECK_INPUTS -> EtsIR replay -> USVM`.
3. `JAZZER_CORPUS -> EtsIR replay -> USVM`.
4. `EXPOSE_INPUTS -> EtsIR replay`, как внешний DSE baseline без USVM.
5. `EXTERNAL_INPUTS + INTERNAL_PBT -> EtsIR replay -> USVM`.
6. `FAST_CHECK + JAZZER + EXPOSE -> dedupe -> EtsIR replay -> USVM` (ensemble).
7. Обратная связь: найденные USVM-входы экспортируются обратно как seeds для
   fast-check/Jazzer.js mutation round.

Во всех вариантах сохраняются отдельно:

- claimed coverage внешнего инструмента;
- mapped coverage через sidecar;
- replay-confirmed EtsIR coverage;
- rejected/unrepresentable/divergent inputs;
- время генерации, replay и symbolic-фазы.

## 5. План реализации

### M0. Зафиксировать протокол и тестовые инварианты

- Этот документ — первоначальная спецификация.
- Не смешивать существующие незакоммиченные engine-fixes с интеграцией.
- Добавить golden fixtures для `NaN`, `-0`, `undefined`, holes, nested objects.
- Зафиксировать seed, версии инструментов, Node/JDK, frontend commit в отчёте.

Критерий готовности: corpus можно прочитать и записать без потери всех значений
v1; неизвестный `kind` даёт явный reject.

### M1. External-input SPI и replay

- `ConcreteInputProvider` и `ConcreteInputCase` в `usvm-ts-pbt`.
- JSON/JSONL ETC reader/writer и stable `methodId`.
- CLI: `--external-inputs`, `--external-only`, `--export-target-manifest`.
- `PbtPhase` сначала реплеит внешний corpus, затем (опционально) запускает
  внутренний генератор.
- TypeProfiler и CoverageTracker получают события от обоих источников.
- В отчёт: producer, imported/executed/rejected/deduplicated case counts.

Критерий готовности: внешний corpus для `HybridSamples.magic` оставляет ровно
ожидаемую ветку, после чего USVM достигает только её.

### M2. fast-check adapter

- Отдельный маленький Node package с pinned fast-check.
- Генерация arbitraries из manifest для primitive/union/array/plain object.
- Возможность пользовательского harness для instance methods и сложных классов.
- Экспорт всех выполненных входов и minimal counterexample после shrinking.
- Differential mode: Node result ↔ EtsIR concrete result.

Критерий готовности: воспроизводимость `(seed, path)`, отсутствие потерь special
numbers, отдельные результаты `internal PBT` и `fast-check` при равном числе runs.

### M3. Jazzer.js adapter

- Генерация fuzz target, декодирующего libFuzzer bytes в typed arguments.
- Сохранение coverage-increasing corpus и crashes в ETC.
- Seed corpus из internal PBT и USVM.
- Не сравнивать количество executions напрямую: основной показатель —
  replay-confirmed EtsIR coverage за одинаковое wall time.

Критерий готовности: Jazzer corpus импортируется без ручного редактирования и
даёт монотонное EtsIR coverage; crash input воспроизводим отдельно.

### M4. Origin sidecar и импорт покрытия

- Патч ts-frontend: CFG terminator/statement несёт origin до `finalize()`.
- Sidecar writer рядом с EtsIR JSON.
- Istanbul importer: source range + branch arm → EtsIR edge.
- V8/c8 importer: диапазоны → source map → TS span → EtsIR edges.
- Отчёт ambiguity: `one-to-one`, `one-to-many`, `unmapped`, `synthetic`.

Критерий готовности: на hand-written fixtures 100% веток `if/else`, loops,
short-circuit и ternary сопоставляются ожидаемым EtsIR edges; неоднозначности не
считаются покрытием молча.

### M5. ExpoSE integration и форк

- Собрать upstream и прогнать минимальные numeric/string/object harness'ы.
- Adapter входов/результатов в ETC.
- Сначала whole-program comparison; затем патч scheduler для target branch IDs
  или source-distance при достаточной стабильности instrumentation IDs.
- Replay каждого ExpoSE witness в Node и в EtsIR.
- Экспорт PBT/Jazzer inputs как начального worklist ExpoSE.

Критерий готовности: ExpoSE и USVM получают одинаковые entry points и бюджеты;
каждый заявленный reached branch имеет отдельный replay status.

### M6. Gillian-JS/JaVerT feasibility

- Определить реально поддерживаемый common subset (Gillian-JS в основном ES5,
  а его symbolic assertion operators имеют важные отличия от JS coercions).
- Транспилировать только совместимые методы и не выдавать unsupported за miss.
- Если не менее заранее выбранной доли методов корпусов запускается без ручной
  переписи, добавить ETC adapter и полноценный baseline.
- Иначе оставить воспроизводимый feature-gap report и microbenchmark suite.

Критерий решения после пилота: доля автоматически запускаемых методов,
корректность concrete replay и стоимость harness transformation.

### M7. Честное сравнение symbolic engines

Основное сравнение: `usvm-ts` против ExpoSE; Gillian-JS добавляется на общем
подмножестве. Два режима:

1. whole-program exploration;
2. directed reachability для остаточных после PBT веток.

Основная метрика — replay-confirmed mapped EtsIR branch coverage при фиксированном
wall-time. Дополнительные:

- AUC кривой coverage/time и time-to-target;
- reached/replay-confirmed ratio;
- число уникальных воспроизводимых exceptions;
- timeouts, solver errors, unsupported methods;
- число solver queries/steps, если инструмент экспортирует их;
- стоимость harness/frontend/replay отдельно от exploration.

Для утверждения «наш движок не хуже» до финальной кампании фиксируется
non-inferiority протокол. Начальный кандидат: медианное покрытие `usvm-ts` не
ниже лучшего внешнего движка более чем на 2 абсолютных процентных пункта на
common subset при одинаковом бюджете. После пилота margin можно изменить один
раз и затем заморозить до основной кампании. Кроме aggregate обязательно
показываются per-project confidence intervals и feature-gap breakdown.

### M8. Многопроектная кампания и статья

Минимальная матрица:

- internal PBT;
- fast-check;
- Jazzer.js;
- usvm-ts only;
- ExpoSE only;
- internal PBT → usvm-ts;
- fast-check → usvm-ts;
- Jazzer.js → usvm-ts;
- ensemble → usvm-ts;
- те же варианты с/без type hints и mutation feedback.

Корпуса: текущие TheAlgorithms/TypeScript, javascript-datastructures-algorithms,
typescript-collections плюс хотя бы один npm-пакет с нетривиальными строками и
объектами. Не менее 10 seed runs на stochastic-конфигурацию; бюджеты и порядок
методов рандомизируются/чередуются, чтобы не путать warming и порядок запуска с
эффектом метода.

## 6. Приоритет исполнения

1. M1 — общий corpus/replay слой.
2. M2 — fast-check.
3. M3 — Jazzer.js.
4. M4 — source/EtsIR mapping.
5. M5 — ExpoSE и сравнение `usvm-ts`.
6. M6 — Gillian-JS feasibility.
7. M7–M8 — замороженный benchmark protocol и полная кампания.

Это сохраняет уже написанный код полезным: конкретный EtsIR-интерпретатор
становится общим арбитром между всеми генераторами, а не частной реализацией
одного PBT-цикла.

## 7. Фактический прогресс (2026-07-18)

- M1: ETC/SPI, replay, CLI и per-producer statistics готовы; 36 Kotlin tests
  проходят на native ts-frontend.
- M2: fast-check 4.9.0 adapter готов; `magic` даёт 3/4, затем USVM 4/4.
- M3: Jazzer.js 4.0.0 adapter готов; независимый corpus даёт 3/4, затем USVM
  4/4.
- M4: jacodb `ba042500` сохраняет source origins; Istanbul/c8, raw V8 и
  ExpoSE/Jalangi importers готовы. На compiled TypeScript fixture source map
  дал 4/4 one-to-one; short-circuit ambiguity остаётся явной.
- M5: ExpoSE `ec03edf8` реально собран headless и запущен; structured path
  inputs автоматически экспортируются в ETC.
- M6: Gillian `b195dfc3` проверен статически; manifest feasibility adapter
  готов. Runtime pilot отложен до изолированного OCaml 5.3/opam/Dune окружения
  и патча экспорта моделей успешных путей.
- M7 pilot: на пяти numeric methods USVM 16/16, ExpoSE 15/16 после EtsIR
  replay; подробности в `08-symbolic-engine-pilot.md`.
- M8 real-project pilot: на трёх pinned проектах USVM и ExpoSE оба дали 14/14
  replay-confirmed ветвей. Первый прогон USVM дал 7/14 и обнаружил три
  исправимых integration defect; baseline и протокол находятся в
  `09-real-project-symbolic-comparison.md`.
