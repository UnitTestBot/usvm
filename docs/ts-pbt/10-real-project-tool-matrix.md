# Матрица генераторов и движков на реальных проектах

Дата pilot baseline: 2026-07-18.

## 1. Общая метрика

Каждый инструмент сначала создаёт собственный набор входов. Покрытие
засчитывается только после запуска этих входов в concrete EtsIR-интерпретаторе.
Таким образом, source-level coverage fast-check/Jazzer/ExpoSE не сравнивается
напрямую с USVM CFG: общий арбитр для всех строк таблицы — одни и те же EtsIR
branch edges.

Пилот использует три pinned entry points:

- TheAlgorithms-TypeScript: `sieveOfEratosthenes`, 8 ветвей;
- typescript-algorithms: `factorialIterative`, 4 ветви;
- typescript-collections: `swap<T>`, 2 ветви.

Конфигурация находится в
`external-tools/symbolic-comparison/real-project-cases.json`. Для stochastic
инструментов зафиксирован seed 42. fast-check генерирует 1000 cases на метод.
Jazzer получает 5 секунд, режим продолжения после исключения и числовой domain
profile `[-100, 100]`; то же преобразование применяется перед реальным вызовом
и при экспорте ETC. ExpoSE и USVM используют протокол из
`09-real-project-symbolic-comparison.md`.

## 2. Результат

| Инструмент | Режим | EtsIR replay coverage | Imported | Реально replayed |
|---|---|---:|---:|---:|
| internal PBT | generated | 14/14 (100%) | — | 81 |
| fast-check 4.9.0 | external corpus | 14/14 (100%) | 3000 | 19 |
| Jazzer.js 4.0.0 | coverage-guided corpus | 14/14 (100%) | 22 | 11 |
| ExpoSE `ec03edf8` | DSE corpus | 14/14 (100%) | 35 | 10 |
| USVM | symbolic witnesses | 14/14 (100%) | — | 9 witnesses |

Все пять конфигураций достигли полного покрытия на этом небольшом pilot subset.
Это подтверждает работоспособность общего exchange/replay слоя, но ещё не
ранжирует инструменты: методы слишком малы и покрытие насыщается быстро.
Количество executions также нельзя читать как performance ranking — генераторы
имеют разные stopping rules, а USVM решает отдельные target-задачи.

## 3. Что пришлось адаптировать

### fast-check

Первоначальный `fc.oneof(edge constants, fc.double())` дал суммарно 9/14:
full-range doubles почти всегда были огромными дробями, бесполезными для циклов
и индексов. Числовой arbitrary теперь смешивает:

- целые и finite doubles в `[-1000, 1000]`;
- явные IEEE edges (`NaN`, infinities, signed zero и safe-integer bounds);
- исходный full-range `fc.double()`.

После этой общей, не benchmark-specific адаптации seed 42 дал 14/14.

### Jazzer.js

Обычный crash mode завершал процесс кодом 77 на первом ожидаемом `throw`.
Добавлен opt-in `--ignore-exceptions`: исключение по-прежнему видно при EtsIR
replay, но fuzzer может продолжить coverage campaign.

Затем `factorialIterative(Infinity)` завершил отдельный input по timeout (код
70). Shared module harness получил числовой domain profile. Он преобразует
неfinite значения в 0 и trunc/clamp-ит остальные; `toCorpusCase` экспортирует
ровно преобразованные аргументы, поэтому между выполненным JS input и EtsIR
replay нет расхождения.

### Concrete EtsIR replay

Первый fast-check corpus обнаружил JVM crash
`Requested array size exceeds VM limit` на `new Array(n)`. Теперь длина массива
проверяется в два этапа:

1. невалидная по ECMAScript длина становится моделируемым `RangeError`;
2. валидная, но превышающая dense-materialization limit, становится
   `ExecutionResult.Diverged`.

То же ограничение применяется к `new Array`, вызову `Array`, присваиванию
`length` и расширению массива записью по индексу. Один патологический input
больше не прерывает анализ остальных cases.

## 4. Автоматическая проверка матрицы

`npm run matrix` принимает прямоугольную сетку отчётов с kind `external`,
`internal` или `symbolic`. Он отклоняет:

- пропущенный tool/method case;
- несовпадающие method signature или EtsIR branch totals;
- internal generation внутри отчёта, объявленного external replay;
- external executions внутри internal PBT;
- смешение report kinds для одного инструмента.

Машиночитаемый результат зафиксирован в
`external-tools/symbolic-comparison/baselines/real-project-tools-2026-07-18.json`.

Следующий статистически содержательный шаг — расширить матрицу на десятки
entry points со строками, объектами и aliasing, выполнить не менее 10 seeds и
считать confidence intervals по проектам. Текущий pilot уже позволяет делать
это без ручного сведения несовместимых coverage форматов.
