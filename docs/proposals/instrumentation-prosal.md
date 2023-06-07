# Instrumentation proposal

## Test-framework:
* Input: UTest (assemble models, concrete values, additional information) +
* Runner accept serialized UTest, assemble concrete values and run instrumented code on it +
* Dynamic instrumentation +

## Instrumentation
Requirements:
* Instrumentation: 
  * trace 
    * at the first stage only jacodb statements +
    * concolic?
  * statics rollback
     * redefine classes in classloader (https://gist.github.com/Saloed/4bc30ca8b1ec8969f7e1307f967058b4)
  * non-deterministic functions mocks ?
  * stdlib mocks ?
    * at the first stage just ignore ?
* Run with timeout +
* Sandbox ?
* Result, diff +
  * smth like UResult (assemble models?) +
  * diffs between input and output +
* Static fields links -
  * for what?