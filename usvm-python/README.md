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

## CPython build

Official instruction: https://devguide.python.org/getting-started/setup-building/.

### Unix

Gradle tasks should do everything automatically.

Task for running `src/main/kotlin/test.kt`: `:usvm-python:runTestKt`.