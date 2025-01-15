# USVM Dataflow TS

## Type Inference

In  order to run type inference on an arbitrary TypeScript project, you need the following:
1. IR dumped into JSON files: either from TS sources or from 
binary ABC/HAP files.
2. USVM with type inference CLI: `usvm-dataflow-ts-all.jar` "fat" JAR.

**NOTE:** the instructions below are given for Linux. If you are using Windows, you need to adjust the paths and commands accordingly, or use WSL. Overall, the process should be similar, and USVM should work on any platform that supports Java.

### ArkTS IR

- Below, we use the term "ArkIR" to refer to the representation of ArkTS inside ArkAnalyzer in a form of TypeScript classes and interfaces, such as `ArkMethod`, `ArkAssignStmt`, `ArkInstanceInvokeExpr`.

- In USVM, we also have a similar model of representing ArkTS, but in the form of Java/Kotlin classes. In order to differentiate between the two models, we use the prefix "Ets" for the classes in USVM, such as `EtsMethod`, `EtsAssignStmt`, `EtsInstanceCallExpr`.

### Setup ArkAnalyzer

First of all, you need to clone the ArkAnalyzer repo. Here, we use the fork of the repo and the specific branch (named `neo/<DATE>`) that is consistent with USVM internals. Note that this branch might change in the future.
```bash
cd ~
git clone -b neo/2024-10-31 https://gitee.com/Lipenx/arkanalyzer arkanalyzer-usvm
cd arkanalyzer-usvm
```

Then, you need to install the dependencies and build the project.
```bash
npm install
npm run build
```

**Note:** after building the ArkAnalyzer project, the script for serializing ArkIR will be located at `out/save/serializeArkIR.js` and can be run with Node.js.

**Note:** you can also use TS script directly using `npx ts-node src/save/serializeArkIR.ts` instead of building the whole project.

### Serialize ArkIR to JSON

Now, you can run the `serializeArkIR` script on your TS project in order to construct its ArkIR representation and dump it into JSON files, which can later be used by USVM.
```bash
node ~/arkanalyzer-usvm/out/src/save/serializeArkIR.js --help 
```
```text
Usage: serializeArkIR [options] <input> <output>

Serialize ArkIR for TypeScript files or projects to JSON

Arguments:
  input                      Input file or directory
  output                     Output file or directory

Options:
  -p, --project              Flag to indicate the input is a project directory (default: false)
  -t, --infer-types [times]  Infer types in the ArkIR
  -v, --verbose              Verbose output (default: false)
  -h, --help                 display help for command
```

If you have a single TS file `sample.ts`, just run the following command:
```bash
node .../serializeArkIR.js sample.ts sample.json
```
The resulting `sample.json` file will contain the ArkIR in JSON format.

If you have a TS project in the `project` directory, use `-p` flag:
```bash
node .../serializeArkIR.js -p project etsir
```
The resulting `etsir` directory will contain the ArkIR in JSON format. The structure of the resulting directory (hierarchy of subfolders) will be the same as the structure of the input project, but all the files will be `*.ts.json`.

_Note:_ We call the result "EtsIR" since it is a modified version of the ArkIR model suitable for serialization. When we load IR from JSONs in USVM (Java/Kotlin), the resulting data model (structure of classes) is very similar to ArkIR in ArkAnalyzer (TypeScript), but has some minor differences. The term "EtsIR" is used to distinguish between the two.

If you have a TS project with multiple modules, run the serialization for each module separately:
```bash
node .../serializeArkIR.js -p project/entry etsir/entry
node .../serializeArkIR.js -p project/common etsir/common
node .../serializeArkIR.js -p project/feature etsir/feature
```

### Type Inference with USVM

In order to run USVM type inference, you need to obtain `usvm-dataflow-ts-all.jar` "fat" JAR (download or build it yourself) and either use it directly or use a wrapper script `src/usvm/inferTypes.ts` in ArkAnalyzer repo.

#### Build `usvm-type-inference` binary

In order to build the USVM binary, you need to clone the USVM repo (and also its dependency `jacodb` in the _sibling directory_) and build the project using Gradle.
```bash
cd ~
git clone -b lipen/usvm-type-inference https://github.com/UnitTestBot/jacodb
git clone -b lipen/type-inference https://github.com/UnitTestBot/usvm
cd usvm
./gradlew :usvm-dataflow-ts:installDist
```
The last command will build the project and create the binary at `usvm-dataflow-ts/build/install/usvm-dataflow-ts/bin/usvm-type-inference` (on Windows, the corresponding "binary" is with `.bat` extension).

#### Build "Fat" JAR

Alternatively, you can build the "fat" JAR (also known as "Uber JAR" or "shadow JAR") that contains all the dependencies.
```bash
./gradlew :usvm-dataflow-ts:shadowJar
```

#### Run Type Inference

You can run the type inference manually using USVM CLI:
```bash
usvm-dataflow-ts/build/install/usvm-dataflow-ts/bin/usvm-type-inference --help
# OR
java -jar usvm-dataflow-ts/build/libs/usvm-dataflow-ts-all.jar --help
```
```text
Usage: infer-types [<options>]

Options:
* -i, --input=<path>                      Input file or directory with IR (required)
* -o, --output=<path>                     Output file with inferred types in JSON format (required)
  -h, --help                              Show this message and exit
```

_Note:_ `-i` option can be supplied multiple times for multi-module projects. All input IR will be merged.

For example, if you have the `project/entry` and `project/common` directories with the dumped ArkIR, you can run the following command:
```bash
java -jar usvm-dataflow-ts/build/libs/usvm-dataflow-ts-all.jar -i project/entry -i project/common -o inferred.json
```

### Type Inference with Wrapper Script

You can also use the wrapper script `src/usvm/inferTypes.ts` from the ArkAnalyzer repo. This script will run the serialization of ArkIR and type inference with USVM in a single command.

```bash
node ~/arkanalyzer-usvm/out/src/usvm/inferTypes.js --help
```
```text
Usage: inferTypes [options] <input>

Arguments:
  input             input directory with ETS project

Options:
  -v, --verbose     Verbose output (default: false)
  -t, --aa-types    Run type inference in ArkAnalyzer (default: false)
  -s, --substitute  Substitute inferred types (default: false)
  -h, --help        display help for command
```

For example:
```bash
node .../inferTypes.js myproject/entry
```
```text
Building scene...
Serializing Scene to '/tmp/2f8aa8b34548b808167a8f6b30121dcc/etsir'...
...
USVM command: ~/usvm/usvm-dataflow-ts/build/install/usvm-dataflow-ts/bin/usvm-type-inference --input=/tmp/2f8aa8b34548b808167a8f6b30121dcc/etsir --output=/tmp/2f8aa8b34548b808167a8f6b30121dcc/inference-result --no-skip-anonymous
...
=== Inferred Types Statistics ===
Total Classes: 10
Total Methods: 305
...
Deserialization successful.
...
Substituting inferred types...
...
Substituting type of local '$temp16' in method '@entry/model/Calculator.ts: _DEFAULT_ARK_CLASS.getFloatNum(unknown, unknown, unknown)' from unknown to number
...
```
