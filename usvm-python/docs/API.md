# API between patched CPython and USVM

Current CPython patch: https://github.com/tochilinak/cpython/pull/4/files.

## SymbolicAdapter

All interaction between CPython and USVM is done through structure `SymbolicAdapter`.

Header: [`symbolicadapter.h`](https://github.com/tochilinak/cpython/blob/wrapper-3.11.9/Include/symbolicadapter.h).

Creation of symbolic adapter:
```c
SymbolicAdapter *create_new_adapter(void *param);
```

`param` is a parameter that will be accessible in all symbolic handlers.

Run function with symbolic tracing:
```c
PyObject *SymbolicAdapter_run(PyObject *self, PyObject *function, Py_ssize_t n, PyObject *const *args, runnable before_call, runnable after_call);
```

- `self`: pointer to SymbolicAdapter.
- `function`: function to run (only `PyFunction` is supported).
- `n`: number of arguments.
- `args` must be an array of tuples of size 2 (concrete and symbolic value for each argument).
- `before_call`: function to call right before execution.
- `after_call`: function to call right after execution.

## Handlers of SymbolicAdapter

Each of the following functions has a default implementation that does nothing, so any of them can be omitted.

All handlers accept `param` from `create_new_adapter` function as their first argument.

### Expected return value of handlers

If return value of handler is `int`:

- 0 on success

- Anything else if an error has happened (exception must be set).

If return value of handler is `PyObject *`:

- Symbolic object on success (or `Py_None` if it is absent).

- 0 if an error has happened (exception must be set).

For descriptions of handlers see comments in `symbolicadapter.h`.
