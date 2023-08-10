# Developer notes

## Working with `git submodule`

Clone repo with submodules:
```
git clone --recurse-submodules [repo]
```

Clone submodule into already cloned project:
```
git submodule update --init
```

Update repo:
```
git pull
git submodule update --init
```

You can always compare commit of submodule from GitHub page and in your local repository.

## CPython build

Official instruction: https://devguide.python.org/getting-started/setup-building/.

### Unix

Gradle tasks should do everything automatically.

Tasks for running `src/test/kotlin/manualTest.kt` (name of task group --- `run`): 

- `:usvm-python:manualTestDebug`: run with debug logging and debug build of CPython
- `:usvm-python:manualTestDebugNoLogs`: run with info logging and debug build of CPython
- `:usvm-python:manualTestRelease`: run with info logging and release build of CPython

## Addition of a method in CPythonAdapter

### Native method

Add the definition of the native method in `CPythonAdapter.java`.

Regenerate `org_usvm_interpreter_CPythonAdapter.h`:

```
cd src/main/java
javah org.usvm.machine.CPythonAdapter
mv org_usvm_interpreter_CPythonAdapter.h ../../../cpythonadapter/src/main/c/include
```

Then implement the corresponding methods in `org_usvm_interpreter_CPythonAdapter.c`.

### Static method that can be called from C code

Implement the method in `CPythonAdapter.java`.

Add the definition in `cpythonadapter/src/main/json/handler_defs.json`. Define `c_name`, `java_name`, `sig`.

The `jmethodID` of the method will be accessible in `ConcolicContext` by the name `handle_<c_name>`.