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

### list_extend

```c
PyObject *(*list_extend)(void *, PyObject *list, PyObject *iterable);
```

Operation https://docs.python.org/3.11/library/dis.html#opcode-LIST_EXTEND.

Expects resulting list as a return value.

### list_append

```c
PyObject *(*list_append)(void *, PyObject *list, PyObject *elem);
```

Operation https://docs.python.org/3.11/library/dis.html#opcode-LIST_APPEND.

Expects resulting list as a return value.

### list_get_size

```c
PyObject *(*list_get_size)(void *, PyObject *list);
```

Asks for symbolic length of symbolic list.

### list_iter

```c
PyObject *(*list_iter)(void *, PyObject *list);
```

Operation `iter()` on a symbolic list.

### list_iterator_next

```c
PyObject *(*list_iterator_next)(void *, PyObject *iterator);
```

Operation `next()` on a symbolic list iterator (list iterator is a result of operation `iter()` on list).

### list_concat

```c
PyObject *(*list_concat)(void *, PyObject *, PyObject *);
```

`+` operation on symbolic lists.

### list_inplace_concat

```c
PyObject *(*list_inplace_concat)(void *, PyObject *, PyObject *);
```

Inplace version of `list_concat`.

### tuple_iter

```c
PyObject *(*tuple_iter)(void *, PyObject *tuple);
```

Operation `iter()` on a symbolic tuple.

### tuple_iterator_next

```c
PyObject *(*tuple_iterator_next)(void *, PyObject *iterator);
```

Operation `next()` on a symbolic tuple iterator (tuple iterator is a result of operation `iter()` on tuple).

### range_iter

```c
PyObject *(*range_iter)(void *, PyObject *range);
```

Operation `iter()` on a symbolic representation of range object.

### range_iterator_next

```c
PyObject *(*range_iterator_next)(void *, PyObject *iterator);
```

Operation `next()` on a symbolic range iterator (range iterator is a result of operation `iter()` on range object).

### symbolic_isinstance

```c
PyObject *(*symbolic_isinstance)(void *, PyObject *on, PyObject *type);
```

Asks for a symbolic result of operation `isinstance`.

`on`: symbolic object on which the operation is performed.

`type`: reference to concrete `PyTypeObject`.

### nb_add

```c
int (*nb_add)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_add` is about to be performed on symbolic objects `left` and `right`.

### nb_subtract

```c
int (*nb_subtract)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_subtract` is about to be performed on symbolic objects `left` and `right`.

### nb_multiply

```c
int (*nb_multiply)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_multiply` is about to be performed on symbolic objects `left` and `right`.

### nb_remainder

```c
int (*nb_remainder)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_remainder` is about to be performed on symbolic objects `left` and `right`.

### nb_divmod

```c
int (*nb_divmod)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_divmod` is about to be performed on symbolic objects `left` and `right`.

### nb_bool

```c
int (*nb_bool)(void *, PyObject *on);
```

Notifies that `nb_bool` is about to be performed on symbolic object `on`.

### nb_int

```c
int (*nb_int)(void *, PyObject *on);
```

Notifies that `nb_int` is about to be performed on symbolic object `on`.

### nb_lshift

```c
int (*nb_lshift)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_lshift` is about to be performed on symbolic objects `left` and `right`.

### nb_rshift

```c
int (*nb_rshift)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_rshift` is about to be performed on symbolic objects `left` and `right`.

### nb_and

```c
int (*nb_and)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_and` is about to be performed on symbolic objects `left` and `right`.

### nb_xor

```c
int (*nb_xor)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_xor` is about to be performed on symbolic objects `left` and `right`.

### nb_or

```c
int (*nb_or)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_or` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_add

```c
int (*nb_inplace_add)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_add` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_subtract

```c
int (*nb_inplace_subtract)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_subtract` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_multiply

```c
int (*nb_inplace_multiply)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_multiply` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_remainder

```c
int (*nb_inplace_remainder)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_remainder` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_lshift

```c
int (*nb_inplace_lshift)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_lshift` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_rshift

```c
int (*nb_inplace_rshift)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_rshift` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_and

```c
int (*nb_inplace_and)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_and` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_xor

```c
int (*nb_inplace_xor)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_xor` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_or

```c
int (*nb_inplace_or)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_or` is about to be performed on symbolic objects `left` and `right`.

### nb_floor_divide

```c
int (*nb_floor_divide)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_floor_divide` is about to be performed on symbolic objects `left` and `right`.

### nb_true_divide

```c
int (*nb_true_divide)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_true_divide` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_floor_divide

```c
int (*nb_inplace_floor_divide)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_floor_divide` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_true_divide

```c
int (*nb_inplace_true_divide)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_true_divide` is about to be performed on symbolic objects `left` and `right`.

### nb_matrix_multiply

```c
int (*nb_matrix_multiply)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_matrix_multiply` is about to be performed on symbolic objects `left` and `right`.

### nb_inplace_matrix_multiply

```c
int (*nb_inplace_matrix_multiply)(void *, PyObject *left, PyObject *right);
```

Notifies that `nb_inplace_matrix_multiply` is about to be performed on symbolic objects `left` and `right`.

### sq_length

```c
int (*sq_length)(void *, PyObject *on);
```

Notifies that `sq_length` is about to be performed on symbolic object `on`.

### sq_concat

```c
int (*sq_concat)(void *, PyObject *left, PyObject *right);
```

Notifies that `sq_concat` is about to be performed on symbolic objects `left` and `right`.

### sq_inplace_concat

```c
int (*sq_inplace_concat)(void *, PyObject *left, PyObject *right);
```

Notifies that `sq_inplace_concat` is about to be performed on symbolic objects `left` and `right`.

### mp_subscript

```c
int (*mp_subscript)(void *, PyObject *storage, PyObject *index);
```

Notifies that `mp_subscript` is about to be performed on symbolic objects `storage` and `index`.

### mp_ass_subscript

```c
int (*mp_ass_subscript)(void *, PyObject *storage, PyObject *index, PyObject *value);
```

Notifies that `mp_ass_subscript` is about to be performed on symbolic objects `storage`, `index` and `value`.

### tp_richcompare

```c
int (*tp_richcompare)(void *, int op, PyObject *left, PyObject *right);
```

Notifies that `tp_richcompare` with operation `op` is about to be performed on symbolic objects `left` and `right`.

### tp_iter

```c
int (*tp_iter)(void *, PyObject *on);
```

Notifies that `tp_iter` is about to be performed on symbolic object `on`.

### tp_iternext

```c
int (*tp_iternext)(void *, PyObject *on);
```

Notifies that `tp_iternext` is about to be performed on symbolic object `on`.
