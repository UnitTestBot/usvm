# API between patched CPython and USVM

All constants are defined in header `SYMBOLIC_API.h`.

## Handler signature

```
PyObject *handler(int event_type, int event_id, int nargs, PyObject *const *args, void *param)
```

## Event types

| event type              | Description                                                           | Expected return type |
|-------------------------|-----------------------------------------------------------------------|:--------------------:|
| `SYM_EVENT_TYPE_STACK`  | Asks for symbolic values that should be put on the interpreter stack. |       `tuple`        |
| `SYM_EVENT_TYPE_NOTIFY` | Notifies about some action.                                           | Ignores return value |
| `SYM_EVENT_TYPE_METHOD` |                                                                       |     `PyObject *`     |

## `SYM_EVENT_TYPE_STACK` events list

For all events empty list may be given as a return value.

|          event id          | Arguments                                      |   Expected return tuple   |
|:--------------------------:|:-----------------------------------------------|:-------------------------:|
| `SYM_EVENT_ID_CREATE_LIST` | Symbolic contents of the list (as Python list) |    `(symbolic_list,)`     |
|    `SYM_EVENT_ID_CONST`    | Python constant                                |  `(constant_as_symbol,)`  |

## `SYM_EVENT_TYPE_NOTIFY` events list

|          event id          | Arguments                                                                                        |
|:--------------------------:|:-------------------------------------------------------------------------------------------------|
| `SYM_EVENT_ID_INSTRUCTION` | One argument: `PyFrameObject*`, frame that is about to execute next instruction.                 |
|    `SYM_EVENT_ID_FORK`     | One argument: `PyObject*` inside `if` condition.                                                 |
| `SYM_EVENT_ID_FORK_RESULT` | One argument: `Py_True` or `Py_False`. Result of concrete condition. Closes `SYM_EVENT_ID_FORK`. |

## `SYM_EVENT_TYPE_METHOD` events list

|        event id        | Arguments                     |
|:----------------------:|:------------------------------|
| `SYM_EVENT_ID_INT_ADD` | 2 arguments: integer operands |
| `SYM_EVENT_ID_INT_GT`  | 2 arguments: integer operands |
| `SYM_EVENT_ID_INT_LT`  | 2 arguments: integer operands |
| `SYM_EVENT_ID_INT_EQ`  | 2 arguments: integer operands |