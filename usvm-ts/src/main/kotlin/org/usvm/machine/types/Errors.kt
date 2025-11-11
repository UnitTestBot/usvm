package org.usvm.machine.types

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature

/*
    Runtime JS exceptions:

    - SyntaxError - When the code is not valid JavaScript (at runtime -- in `eval`).
    - ReferenceError - When a variable that does not exist is accessed.
    - TypeError - When a value is not of the expected type.
    - RangeError - When a numeric value is outside of its allowed range.
    - URIError - When there is an error in encodeURI() or decodeURI().
    - EvalError - When the eval() function is used incorrectly.
    - InternalError - When an internal error in the JavaScript engine occurs. [non-standard]
    - AggregateError - A single error that represents multiple errors. Introduced in ES2021.
        For example, when Promise.any() rejects, it rejects with
        an AggregateError containing all the individual errors.
    - Custom Errors - User-defined error types that extend the built-in Error class.

 */

/*

== Basic Error Interface

```ts
interface Error {
    name: string;       // Error type name
    message: string;    // Human-readable description
    stack?: string;     // Stack trace (non-standard but universal)
}

interface ErrorConstructor {
    new(message?: string): Error;
}

interface ReferenceErrorConstructor extends ErrorConstructor {
    new(message?: string): ReferenceError;
}

const err = new ReferenceError("x is not defined");
```

== Inheritance Hierarchy

    Error (base)
    ├── SyntaxError
    ├── ReferenceError
    ├── TypeError
    ├── RangeError
    ├── URIError
    ├── EvalError
    └── AggregateError

== TypeScript Definition (`lib.d.ts`)

```ts
declare var Error: ErrorConstructor;
declare var ReferenceError: ReferenceErrorConstructor;
declare var TypeError: TypeErrorConstructor;
declare var RangeError: RangeErrorConstructor;
declare var SyntaxError: SyntaxErrorConstructor;
declare var URIError: URIErrorConstructor;
```

 */

object EtsErrorTypes {
    val Error = etsErrorClass("Error")
    val SyntaxError = etsErrorClass("SyntaxError")
    val ReferenceError = etsErrorClass("ReferenceError")
    val TypeError = etsErrorClass("TypeError")
    val RangeError = etsErrorClass("RangeError")
    val URIError = etsErrorClass("URIError")
    val EvalError = etsErrorClass("EvalError")
    val AggregateError = etsErrorClass("AggregateError", etsLibEs2020File)
}

private val etsLibEs5File = EtsFileSignature("ES5", "lib.es5.d.ts")
private val etsLibEs2020File = EtsFileSignature("ES2020", "lib.es2020.d.ts")

private fun etsErrorClass(name: String, file: EtsFileSignature = etsLibEs5File): EtsClassType =
    EtsClassType(EtsClassSignature(name, file))
