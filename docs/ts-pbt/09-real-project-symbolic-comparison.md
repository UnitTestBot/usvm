# Сравнение USVM и ExpoSE на реальных TypeScript-проектах

Дата фиксации baseline: 2026-07-18.

## 1. Протокол пилота

- общий арбитр — concrete replay в EtsIR-интерпретаторе;
- frontend — native ts-frontend из jacodb `ed94d48c`;
- внешний symbolic engine — ExpoSE `ec03edf8`, Node.js 21.7.2 и системный Z3;
- ExpoSE получает до 5 секунд на method campaign;
- USVM запускается в `SYMBOLIC_ONLY`, с лимитом 5 секунд на target и
  `maxArraySize = 1000`;
- для generic `swap<T>` ExpoSE явно специализируется как `T = number`, тогда
  как USVM анализирует исходную generic-сигнатуру;
- критерий non-inferiority: USVM может уступить не более двух абсолютных
  процентных пунктов replay-confirmed branch-edge coverage.

Время движков приведено только диагностически: границы frontend/process startup
у них различаются, поэтому сравнивать эти числа как полноценный performance
benchmark нельзя.

## 2. Результат

| Проект | Метод | Ветвей | ExpoSE после EtsIR replay | USVM после EtsIR replay |
|---|---|---:|---:|---:|
| TheAlgorithms-TypeScript | `sieveOfEratosthenes` | 8 | 8/8 | 8/8 |
| typescript-algorithms | `factorialIterative` | 4 | 4/4 | 4/4 |
| typescript-collections | `swap<T>` | 2 | 2/2 | 2/2 |
| **Итого** | 3 метода | **14** | **14/14 (100%)** | **14/14 (100%)** |

Разница USVM − ExpoSE равна `0.0 pp`; пилотный non-inferiority критерий
выполнен. ExpoSE построил 35 путей, один из них завершился ошибкой. USVM создал
10 branch-target задач, решил 9 и подтвердил replay всех 9 witnesses.

На `sieveOfEratosthenes` одна непосредственная target-задача для внутренней
ветви не укладывается в пять секунд. Это не оставляет пробела в итоговом
покрытии: witness для следующей ветви проходит через неё, а concrete replay
подтверждает обе стороны. Поэтому основная метрика специально считается по
покрытию replay, а не по числу локально решённых target-задач.

## 3. Что обнаружило сравнение

Первый честный запуск давал USVM только 7/14 против ExpoSE 14/14. Причины были
не в отсутствии нужных путей у solver, а в трёх дефектах интеграции:

1. ts-frontend терял тип элемента у `new Array<boolean>(n)` и у контекстно
   типизированного `const xs: number[] = []`. Исправлено в jacodb `ed94d48c`.
2. `T[]` переводился в слишком конкретный symbolic type, из-за чего initial
   state для `swap<T>` был UNSAT. Generic type теперь нормализуется в constraint
   либо `unknown`.
3. Размер только что созданного symbolic-массива ограничивался сверху
   `Int.MAX_VALUE`, хотя остальная машина использует `maxArraySize`. Единая
   граница резко уменьшила пространство поиска для `sieve`.

Таким образом, внешний движок здесь сработал не только как конкурентный
baseline, но и как oracle для обнаружения систематических пробелов в нашей
frontend/machine связке.

## 4. Воспроизводимые артефакты

Машиночитаемый baseline лежит в
`external-tools/symbolic-comparison/baselines/real-project-2026-07-18.json`.
Aggregator `npm run campaign` принимает по три отчёта на case: USVM replay
внешнего корпуса, чистый USVM symbolic report и сырой ExpoSE report. Он сводит
только replay-confirmed EtsIR branch edges и автоматически проверяет margin.

Это всё ещё пилот, а не статистическое доказательство. Для полной кампании
остаются десятки entry points, несколько seeds для stochastic generators,
объектные и строковые задачи, confidence intervals и отдельный feature-gap
breakdown.
