### Handler signature

```
PyObject *handler(int signal_type, int signal_id, int nargs, PyObject *const *args, void *param)
```

### Signal types

| Signal type | Description                                                           | Expected return type |
|:-----------:|-----------------------------------------------------------------------|:--------------------:|
|   `STACK`   | Asks for symbolic values that should be put on the interpreter stack. |        `list`        |
|  `NOTIFY`   | Notifies about some action.                                           |        `None`        |

### `STACK` signals list

For all signals empty list may be given as a return value.

| Signal id |                   Arguments                    |  Expected return list  |
|:---------:|:----------------------------------------------:|:----------------------:|
|  `LIST`   | Symbolic contents of the list (as Python list) |   `[symbolic_list]`    |
|  `CONST`  |                Python constant                 | `[constans_as_symbol]` |

### `NOTIFY` signals list


|   Signal id   | Arguments                                                      |
|:-------------:|:---------------------------------------------------------------|
| `INSTRUCTION` | One argument: `int`, number of instruction to be executed      |
|    `FORK`     | One argument: `PyObject*` inside `if` condition.               |
| `FORK_RESULT` | One argument: `True` or `False`. Result of concrete condition. |
