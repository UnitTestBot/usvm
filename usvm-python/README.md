# Developer notes

## Getting started

By default, tasks in module `usvm-python` that require `CPython` are turned off (including `usvm-python:test`).
To turn them on, first you need to set up `CPython`. For that:

1. Clone `CPython` as repository submodule ([refer to the section about submodules](#working-with-git-submodule)).

2. If you are using Unix, you also need to install optional dependencies  ([refer to the section about CPython build](#cpython-build)).

After these steps, add gradle property `cpythonActivated=true`. This can be done in `GRADLE_USER_HOME` directory 
([about](https://docs.gradle.org/current/userguide/directory_layout.html#dir:gradle_user_home))
or during gradle command in CLI:

```
./gradlew <task> -PcpythonActivated=true
```

Now, you should be able to run Gradle task `:usvm-python:test`.

## Documentation on module `usvm-python`

See folder `usvm-python/docs`.

## Working with `git submodule`

Clone repository with submodules:
```
git clone --recurse-submodules [repo]
```

Clone submodule into already cloned project:
```
git submodule update --init
```

Update repository:
```
git pull
git submodule update --init
```

You can always compare commit of submodule from GitHub page and in your local repository.

## CPython build

Official instruction: https://devguide.python.org/getting-started/setup-building/.

Gradle tasks for building and running were tested on Windows and Ubuntu.

1. Only for Unix. Install optional dependencies.
    - Official instruction: https://devguide.python.org/getting-started/setup-building/#install-dependencies
    - __Short version (Ubuntu)__. Install the following packages with apt:
      ```
      build-essential gdb lcov pkg-config \
      libbz2-dev libffi-dev libgdbm-dev libgdbm-compat-dev liblzma-dev \
      libncurses5-dev libreadline6-dev libsqlite3-dev libssl-dev \
      lzma lzma-dev tk-dev uuid-dev zlib1g-dev
      ```
     The main package from those is `libssl-dev`. It is needed for pip to work.

2. Use Gradle tasks to do the rest.
    
    - Task to run tests (see `src/test/resources/samples` and `src/test/kotlin/org/usvm/samples`):

      - `:usvm-python:test` (name of task group --- `verification`)

    - Tasks for running `src/test/kotlin/manualTest.kt` (name of task group --- `run`): 
    
      - `:usvm-python:manualTestDebug`: run with debug logging and debug build of CPython
      - `:usvm-python:manualTestDebugNoLogs`: run with info logging and debug build of CPython

## Addition of a method in CPythonAdapter

### Native method

Add the definition of the native method in `CPythonAdapter.java`.

Regenerate `org_usvm_interpreter_CPythonAdapter.h`:

```
cd usvm-python-main/src/main/java
javah org.usvm.interpreter.CPythonAdapter
mv org_usvm_interpreter_CPythonAdapter.h ../../../../cpythonadapter/src/main/c/include
```

Then implement the corresponding methods in `org_usvm_interpreter_CPythonAdapter.c`.

### Static method that can be called from C code

Implement the method in `CPythonAdapter.java`.

Annotate the method with `CPythonAdapterJavaMethod(cName = <c_name>)`.

The `jmethodID` of the method will be accessible in `ConcolicContext` by the name `handle_<c_name>`.
