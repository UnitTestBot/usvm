# Повторная кампания: список targets и реальные source entry points

Дата: 2026-07-19.

## Краткий вывод

Повторный прогон подтвердил, что USVM даёт реальный выигрыш, но он сильно
зависит от множества оставшихся ветвей.

- На 84 экспортируемых source entry points из трёх проектов USVM отдельно
  replay-подтвердил 207 target witnesses и покрыл 240/422 ветвей (56.87%).
- Внутренний PBT покрыл 305/422, а PBT → USVM — 309/422: строгий прирост
  составил 4 ветви, или 0.95 процентного пункта.
- На общем primitive subset из 42 entry points ExpoSE → USVM дал самый большой
  выигрыш: 162/236 → 202/236, то есть +40 ветвей и +16.95 pp.
- Jazzer → USVM дал +7 ветвей, internal PBT → USVM и fast-check → USVM — по
  +3. После внешнего ensemble новых ветвей не осталось: 220/236 → 220/236.

Следовательно, проблема не в том, что USVM «ничего не делает». Сам движок
покрывает много ветвей и хорошо дополняет структурно иной corpus. Малый прирост
после нашего PBT означает, что PBT оставляет USVM преимущественно тяжёлый хвост:
unsupported runtime semantics, callbacks, objects, pointer calls и ветви с
большим symbolic/concrete replay gap.

Наивное пакетирование всех остаточных targets одного метода уменьшило число
запусков `TsMachine`, но само по себе не ускорило анализ. На одинаковом
primitive subset число machine runs уменьшилось со 136 до 63, а суммарное
symbolic wall time выросло с 39.498 s до 67.782 s. Причина: один недостижимый
target удерживает весь batch до общего таймаута. Значит, список targets нужно
сохранить, но добавить progress-based stopping или небольшие динамические
shards.

## Что было изменено

1. Все ветви, оставшиеся после concrete phase, передаются одним списком в один
   `TsMachine.analyze` для метода. Искусственный общий root и отдельный
   target-constructor удалены: как в JVM taint analysis, machinery получает
   коллекцию независимых target roots и раскрывает их в ходе анализа.
2. Для каждой branch edge остаётся независимая цепочка
   `entry → if statement → successor`, чтобы отличать ребро CFG от простого
   посещения successor через другого predecessor.
3. Состояние не сохраняется до конца batch: inputs разрешаются сразу при
   достижении terminal target, потому что машина продолжает менять live state.
4. В отчёт добавлены реальные aggregate `machineRuns`, `steps` и `wallMs`.
   Per-target время и steps теперь означают effort на момент достижения и не
   суммируются как независимые запуски.
5. Hint fallback, если он включён, запускается одним вторым batch только для
   target roots, не достигнутых с hints.
6. Solver оставлен без изменений: **Yices**.
7. Source mapper теперь отдельно экспортирует все `sourceCallable` method IDs.
   В основную выборку входят только branch-bearing free/static EtsIR methods с
   однозначным origin mapping в экспортированную top-level TS-функцию. Lowered
   nested callbacks и closures самостоятельными entry points не считаются.

## Проекты и выборка

| Проект | Commit | Файлов | Все EtsIR methods / branches | Source entry points / branches | Primitive entry points / branches |
|---|---|---:|---:|---:|---:|
| TheAlgorithms-TypeScript, `maths` | `19b4ced86c99` | 42 | 62 / 264 | 41 / 232 | 33 / 186 |
| javascript-datastructures-algorithms, `src` | `e8ee8f9b8a07` | 62 | 312 / 694 | 33 / 142 | 9 / 50 |
| typescript-collections, `src/lib` | `309bb1b6955b` | 17 | 215 / 454 | 10 / 48 | 0 / 0 |
| **Итого** | — | **121** | **589 / 1412** | **84 / 422** | **42 / 236** |

`sourceCallable` допускает arrays, objects, generics и callbacks, если функция
является реальным экспортируемым entry point. Primitive subset нужен только
для честного запуска source-level fast-check/Jazzer/ExpoSE harnesses.

## Протокол

- native TypeScript frontend из соседнего `jacodb`;
- source revisions закреплены commit IDs из таблицы;
- seed `20260719`;
- internal PBT и fast-check: до 100 generated cases на метод;
- Jazzer.js: ранее сохранённый corpus с budget 1 s на функцию;
- ExpoSE: ранее сохранённый corpus с budget 2 s на функцию;
- USVM: Yices, номинально 1 s на target; для batch сохранён старый максимальный
  бюджет `target count × 1 s`;
- ветвь засчитывается только после concrete EtsIR replay;
- один fixed-seed прогон, не confidence interval.

Время Jazzer/ExpoSE в таблицах ниже — только импорт и EtsIR replay уже созданных
корпусов. Их исходное создание заняло 115.123 s и 64.543 s соответственно.

## Широкая выборка source entry points

| Режим | Строгое покрытие | Общее время | Symbolic targets | Reached | Replay-confirmed | Machine runs |
|---|---:|---:|---:|---:|---:|---:|
| internal PBT | 305/422 (72.27%) | 1.419 s | — | — | — | — |
| USVM отдельно | 240/422 (56.87%) | 138.703 s | 422 | 238 | 207 | 84 |
| internal PBT → USVM | **309/422 (73.22%)** | 19.775 s | 117 | 25 | 2 | 31 |

У USVM-only число покрытых ветвей больше числа replay-confirmed target
witnesses, потому что один concrete replay часто проходит несколько branch
edges. По той же причине два replay-confirmed residual targets в broad hybrid
дали четыре новые ветви.

### По проектам

| Проект | Ветвей | PBT | USVM | PBT → USVM | Прирост |
|---|---:|---:|---:|---:|---:|
| TheAlgorithms/maths | 232 | 202 | 144 | 203 | +1 |
| javascript-datastructures-algorithms | 142 | 89 | **90** | 92 | +3 |
| typescript-collections | 48 | 14 | 6 | 14 | 0 |
| **Итого** | **422** | **305** | **240** | **309** | **+4** |

На втором проекте USVM отдельно уже немного лучше internal PBT. Нулевой
hybrid-прирост на `typescript-collections` объясняется не отсутствием symbolic
поиска: 14 из 34 residual targets были solver-reached, но ни один witness не
воспроизвёл target edge в concrete interpreter.

## Сравнение всех инструментов на primitive entry points

### Standalone

| Инструмент | Coverage | Replay/analysis time | Target reached / replay |
|---|---:|---:|---:|
| internal PBT | **217/236 (91.95%)** | 1.040 s | — |
| fast-check 4.9.0 | 213/236 (90.25%) | 1.482 s | — |
| Jazzer.js 4.0.0 | 211/236 (89.41%) | 0.677 s | — |
| ExpoSE | 162/236 (68.64%) | 0.196 s | — |
| USVM | 169/236 (71.61%) | 111.404 s | 147 / 144 |
| внешний ensemble | **220/236 (93.22%)** | 1.463 s | — |

### Гибриды

| Конфигурация | До USVM | После USVM | Строгий прирост | Symbolic time | Runs | Targets / reached / replay |
|---|---:|---:|---:|---:|---:|---:|
| internal PBT → USVM | 217 | 220 (93.22%) | +3 | 8.856 s | 7 | 19 / 2 / 1 |
| fast-check → USVM | 213 | 216 (91.53%) | +3 | 11.832 s | 10 | 23 / 2 / 1 |
| Jazzer → USVM | 211 | 218 (92.37%) | +7 | 8.582 s | 13 | 25 / 8 / 7 |
| ExpoSE → USVM | 162 | 202 (85.59%) | **+40** | 33.212 s | 26 | 74 / 34 / 33 |
| ensemble → USVM | 220 | 220 (93.22%) | 0 | 5.300 s | 7 | 16 / 1 / 0 |

Результат ExpoSE → USVM особенно показателен. ExpoSE на этом denominator слабее
PBT, но его остаточные ветви структурно лучше подходят нашему solver. После
сильного ensemble USVM закономерно не добавляет покрытие: остаётся уже не
случайный хвост, а почти исключительно несовместимый с текущей моделью runtime.

## Один target на запуск против списка targets

Сравнение выполнено на тех же 42 primitive entry points и тех же сохранённых
concrete corpora. Старый отчёт не включал target, если он был попутно покрыт
replay более раннего witness; новый batch фиксирует полный исходный residual
список. Поэтому `targets` между старыми и новыми raw reports не всегда равны,
но входное покрытие и итоговые branch edges сравнимы.

| Гибрид | Старое symbolic time | Batch symbolic time | Ratio | Старые runs | Batch runs | Разница покрытия |
|---|---:|---:|---:|---:|---:|---:|
| internal PBT → USVM | 6.051 s | 8.856 s | 1.46× | 17 | 7 | 0 |
| fast-check → USVM | 8.771 s | 11.832 s | 1.35× | 21 | 10 | 0 |
| Jazzer → USVM | 5.825 s | 8.582 s | 1.47× | 25 | 13 | 0 |
| ExpoSE → USVM | 13.439 s | 33.212 s | 2.47× | 57 | 26 | -1 |
| ensemble → USVM | 5.412 s | 5.300 s | 0.98× | 16 | 7 | 0 |
| **Итого** | **39.498 s** | **67.782 s** | **1.72×** | **136** | **63** | **-1** |

Список targets сокращает solver/machine initialization, но текущий stop rule
`stop when all targets are removed OR total batch timeout` делает unreachable
tail слишком дорогим. ExpoSE batch также потерял одну ветвь относительно
старого порядка поиска: 202 вместо 203. Поэтому пакетирование в текущем виде —
архитектурно правильная база, но ещё не performance optimization.

## Что улучшать дальше

1. **Progress-based stopping.** Останавливать batch, если в течение одного
   `perTargetTimeout` не удалён ни один terminal target. Общий `N × timeout`
   оставить только safety ceiling. Это сохраняет совместный поиск, но не даёт
   одному unreachable target съесть весь остаток бюджета.
2. **Небольшие shards вместо одного монолитного batch.** Группировать targets
   по method/CFG region и запускать, например, 4–8 roots за раз. Достигнутые
   witness replay-ить между shards, чтобы удалять попутно покрытые edges.
3. **Capability routing до solver.** Не тратить основной budget на targets,
   чей обязательный prefix содержит `yield`, `SpreadElement`, неподдержанный
   pointer call, callback/function value или заведомо неразрешимый runtime API.
   Их следует маркировать отдельно, а не смешивать с arithmetic targets.
4. **Использовать coverage frontier.** Для residual edge сохранять кратчайший
   уже concrete-достигнутый prefix и направлять symbolic поиск от ближайшей
   границы, а не только по CFG distance от entry.
5. **Сокращать replay gap.** Приоритетные классы: function/callback values,
   pointer-call resolution, generic/object input construction, arrays/maps,
   `yield`, spread и согласование approximations между symbolic и concrete
   interpreters.
6. **Отдельно измерить alternative JS symbolic engine.** Сравнение должно идти
   через те же source mappings, exported witnesses и обязательный EtsIR replay;
   raw path count без replay не сопоставим с текущими числами.
7. **После стабилизации — multi-seed campaign.** Нужны минимум 10 seeds и
   confidence intervals; текущая кампания предназначена для архитектурного
   сравнения, а не статистического ранжирования близких результатов.

## Артефакты

- `usvm-ts-pbt/benchmarks/results/batched-entrypoints-2026-07-19/summary.json`
  — полная машинно-читаемая сводка;
- `usvm-ts-pbt/benchmarks/results/batched-entrypoints-2026-07-19/coverage.csv`
  — строки по проектам, режимам и denominator;
- `usvm-ts-pbt/benchmarks/summarize-batched-entrypoints.mjs` — воспроизводимая
  агрегация новых raw reports и старого per-target baseline;
- `usvm-ts-pbt/benchmarks/source-targets.cjs` — source-to-EtsIR mapping и вывод
  `sourceCallable`/primitive method ID lists;
- raw reports находятся в
  `/tmp/representative-ts-pbt-batched-entrypoints-20260719`;
- старый baseline находится в `/tmp/representative-ts-pbt-20260719`.
