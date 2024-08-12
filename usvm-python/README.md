# Developer notes

## Getting started

By default, tasks in module `usvm-python` that require `CPython` are turned off (including `usvm-python:test`).
To turn them on, first you need to set up `CPython`. For that:

1. Clone `CPython` as repository submodule ([refer to the section about submodules](#working-with-git-submodule)).

2. Install dependencies ([refer to the section about CPython build](#cpython-build)). This step is mandatory for all operating systems.

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

Gradle tasks for building and running were tested on Windows, Ubuntu and MacOS 14.4.1.

### Windows

For Windows you need MSBuild (see https://devguide.python.org/getting-started/setup-building/#windows).

Note that you need `Python native development tool` component. You may also need `Desktop development with C++` component.

### Linux and MacOS

For these OS you need to install optional dependencies.
  
  - Only for Linux.
    - Official instruction: https://devguide.python.org/getting-started/setup-building/#install-dependencies
    - __Short version (Ubuntu)__. Install the following packages with apt:
     ```
     build-essential gdb lcov pkg-config \
     libbz2-dev libffi-dev libgdbm-dev libgdbm-compat-dev liblzma-dev \
     libncurses5-dev libreadline6-dev libsqlite3-dev libssl-dev \
     lzma lzma-dev tk-dev uuid-dev zlib1g-dev
     ```
    The main package from those is `libssl-dev`. It is needed for pip to work.
  - Only for MacOS:
    - Install openssl:
    ```
    brew install openssl@3.0
    
    ```
    - Set a gradle property pointing to OpenSSL location ([about `GRADLE_USER_HOME` directory](https://docs.gradle.org/current/userguide/directory_layout.html#dir:gradle_user_home)):
    ```
    cpython.ssl.path=/opt/homebrew/opt/openssl    
    ```

### After dependecies are installed

Use Gradle tasks to do the rest.
    
- Task to run tests (see `src/test/resources/samples` and `src/test/kotlin/org/usvm/samples`):

  - `:usvm-python:test` (name of task group --- `verification`)

- Tasks for running `src/test/kotlin/manualTest.kt` (name of task group --- `run`): 
    
  - `:usvm-python:manualTestDebug`: run with debug logging and debug build of CPython
  - `:usvm-python:manualTestDebugNoLogs`: run with info logging and debug build of CPython

## Structure of `usvm-python`

`usvm-python` has several internal Gradle modules. Here is a description for them.

### Root module `usvm-python`
  
  Puts all internal modules together. Tests are declared here.

### Module `usvm-python:usvm-python-main`

  Main part of `usvm-python` symbolic machine.

### Module `usvm-python:cpythonadapter`

  This module contains CPython as git submodule in folder `cpython`.

  The code in `src` folder is a native part of the symbolic machine.
  It is supposed to bind Python and USVM in one process.
  In `usvm-python-main` the binding point is class `CPythonAdapter.java`.

### Module `usvm-python:usvm-python-annotations`

  Declares several annotations for `CPythonAdadpter.java`.
  They are used to automatically generate some C code.
  After build, the generated code can be found in folder
  `cpythonadapter/build/adapter_include`.

### Module `usvm-python:usvm-python-runner`

  JVM library for using `usvm-python` in other applications (such as UTBot).

### Module `usvm-python:usvm-python-commons`

  Code that is used both in `usvm-python-runner` and `usvm-python-main`.

## Addition of a method in CPythonAdapter

### Native method

Add the definition of the native method in `CPythonAdapter.java`.

Header `org_usvm_interpreter_CPythonAdapter.h` should be generated automatically before building `usvm-python:cpythonadapter` module. It can be found in the build directory. The Gradle task that is responsible for that is `usvm-python-main:build`.

Then implement the corresponding methods in `org_usvm_interpreter_CPythonAdapter.c`.

### Static method that can be called from C code

Implement the method in `CPythonAdapter.java`.

Annotate the method with `CPythonAdapterJavaMethod(cName = <c_name>)`.

The `jmethodID` of the method will be accessible in `ConcolicContext` by the name `handle_<c_name>`.
