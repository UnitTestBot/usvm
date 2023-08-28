# API between patched CPython and USVM

Current CPython patch: https://github.com/tochilinak/cpython/pull/3/files.

## SymbolicAdapter

All interaction between CPython and USVM is done through structure `SymbolicAdapter`.

Header: `symbolicadapter.h`.

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

### instruction

```c
int (*instruction)(void *, PyFrameObject *frame);
```

Notifies that a new byte code instruction is about to be executed.

### fork_notify

```c
int (*fork_notify)(void *, PyObject *on);
```

Notifies that execution is about to fork on symbolic object `on`.

### fork_result

```c
int (*fork_result)(void *, PyObject *on, int result);
```

Notifies that the result of the fork on `on` is `result`.

### function_call

```
int (*function_call)(void *, PyObject *code);
```

Notifies that `code` is about to be executed.

### function_return

```
int (*function_return)(void *, PyObject *code);
```

Notifies that execution is about to return from `code`.

### unpack

```
int (*unpack)(void *, PyObject *iterable, int count);
```

Notifies that `iterable` is about to be unpacked into `count` elements on the interpreter stack.

### load_const

```
PyObject *(*load_const)(void *, PyObject *obj);
```

Asks for symbolic representation of constant `obj`.

### create_list

```
PyObject *(*create_list)(void *, PyObject **elems);
```

Asks for symbolic representation of list.

`elems`: symbolic representation of contents. The array ends with `NULL`.

### create_tuple

```c
PyObject *(*create_tuple)(void *, PyObject **elems);
```

Like `create_list` but for `tuple`.

### create_range

```c
PyObject *(*create_range)(void *, PyObject *start, PyObject *stop, PyObject *step);
```

Asks for symbolic representation of `range` object.

`start`, `stop` and `step` are symbolic representations of range parameters.

### gt_long

```c
PyObject *(*gt_long)(void *, PyObject *left, PyObject *right);
```

`>` operation on symbolic integers `left` and `right`.

### lt_long

```c
PyObject *(*lt_long)(void *, PyObject *left, PyObject *right);
```

`<` operation on symbolic integers `left` and `right`.

### eq_long

```c
PyObject *(*eq_long)(void *, PyObject *left, PyObject *right);
```

`==` operation on symbolic integers `left` and `right`.

### ne_long

```c
PyObject *(*ne_long)(void *, PyObject *left, PyObject *right);
```

`!=` operation on symbolic integers `left` and `right`.

### le_long

```c
PyObject *(*le_long)(void *, PyObject *left, PyObject *right);
```

`<=` operation on symbolic integers `left` and `right`.

### ge_long

```c
PyObject *(*ge_long)(void *, PyObject *left, PyObject *right);
```

`>=` operation on symbolic integers `left` and `right`.

### add_long

```c
PyObject *(*add_long)(void *, PyObject *left, PyObject *right);
```

`+` operation on symbolic integers `left` and `right`.

### sub_long

```c
PyObject *(*sub_long)(void *, PyObject *left, PyObject *right);
```

`-` operation on symbolic integers `left` and `right`.

### mul_long

```c
PyObject *(*mul_long)(void *, PyObject *left, PyObject *right);
```

`*` operation on symbolic integers `left` and `right`.

### div_long

```c
PyObject *(*div_long)(void *, PyObject *left, PyObject *right);
```

`//` operation on symbolic integers `left` and `right`.

### rem_long

```c
PyObject *(*rem_long)(void *, PyObject *left, PyObject *right);
```

`%` operation on symbolic integers `left` and `right`.

### (skip)

TODO: `pow_long`, `bool_and`.

### list_get_item

```c
PyObject *(*list_get_item)(void *, PyObject *storage, PyObject *index);
```

Operation `storage[index]` (when concrete implementation is `PyList_Type.tp_as_mapping->mp_subscript`).

### list_set_item

```c
int (*list_set_item)(void *, PyObject *storage, PyObject *index, PyObject *value);
```

Notifies about `PyList_Type.tp_as_mapping->mp_ass_subscript`. All arguments are symbolic representations.