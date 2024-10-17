# USVM Dataflow TS

## Type Inference

In  order to run type inference on an arbitrary TypeScript project, you need the following:
1. IR dumped into JSON files: either from TS sources or from 
binary ABC/HAP files.
2. USVM type inference CLI: either `usvm-type-inference` binary or `usvm-dataflow-ts-all.jar` "fat" JAR.

**NOTE:** the instructions below are given for Linux. If you are using Windows, you need to adjust the paths and commands accordingly, or use WSL. Overall, the process should be similar, and USVM should work on any platform that supports Java.

### ArkTS IR

- Below, we use the term "ArkIR" to refer to the representation of ArkTS inside ArkAnalyzer in a form of TypeScript classes and interfaces, such as `ArkMethod`, `ArkAssignStmt`, `ArkInstanceInvokeExpr`.

- In USVM, we also have a similar model of representing ArkTS in a form of Java/Kotlin classes. In order to differentiate between the two, we use the prefix "Ets" for the classes in USVM, such as `EtsMethod`, `EtsAssignStmt`, `EtsInstanceCallExpr`.

### Setup ArkAnalyzer

First of all, you need to clone the ArkAnalyzer repo. Here, we use the fork of the repo and the specific branch that is consistent with USVM internals. Note that this branch might change in the future.
```bash
cd ~
git clone -b neo/2024-10-17 https://gitee.com/Lipenx/arkanalyzer arkanalyzer-usvm
cd arkanalyzer-usvm
```

Then, you need to install the dependencies and build the project.
```bash
npm install
npm run build
```

Note that after building the ArkAnalyzer project, the script for serializing ArkIR will be located at `out/save/serializeArkIR.js` and can be run with Node.js.

### Serialize ArkIR to JSON

Now, you can run the `serializeArkIR` script on your TS project in order to construct its ArkIR representation and dump it into JSON files, which can later be used by USVM.
```bash
node ~/arkanalyzer-usvm/out/save/serializeArkIR.js --help 
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

In order to run type inference on the dumped ArkIR, you need to use the USVM type inference CLI. You can either use the `usvm-type-inference` binary or the `usvm-dataflow-ts-all.jar` "fat" JAR.

#### Build `usvm-type-inference` binary

In order to build the binary, you need to clone the USVM repo and build the project.
```bash
cd ~
git clone -b lipen/type-inference https://github.com/UnitTestBot/usvm
cd usvm
./gradlew :usvm-dataflow-ts:installDist
```
The last command will build the project and create the binary at `usvm-dataflow-ts/build/install/usvm-dataflow-ts/bin/usvm-type-inference`.

#### Build "Fat" JAR

Alternatively, you can build the "fat" JAR (also known as "Uber JAR" or "shadow JAR") that contains all the dependencies.
```bash
./gradlew :usvm-dataflow-ts:shadowJar
```

#### Run Type Inference

Now, you can run the type inference:
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

### Transfer Inferred Types to ArkAnalyzer

In order to transfer the inferred types back to ArkAnalyzer, you need to run the `deserializeInferredTypes` script from ArkAnalyzer repo.

```bash
node ~/arkanalyzer-usvm/out/src/usvm/deserializeInferredTypes.js inferred.json
```
```text
=== Inferred Types Statistics ===
Total Classes: 10
Total Methods: 312

<SNIP>

Class '@common/InputComponent.ets/entry: InputComponent'
  Fields: 2
    Field 'expression': any
    Field 'result': any
  Methods: 1
    Method '@instance_init'

<SNIP>

Method '@pages/Index.ets/entry: Index.AnonymousMethod-@instance_init-2({ _: 'StringType' })'
  Args: 1
    Arg0: any
  Return: unknown
  Locals: 5
    Local 'DATA_CHANGE': { _: 'StringType' }
    Local 'TAG': { _: 'StringType' }
    Local 'EXIT': { _: 'StringType' }
    Local 'key': any
    Local 'this': @pages/Index.ets/entry: Index

<SNIP>

Deserialization successful.
```

_Note:_ the output format might change in the future. The deserialization script itself is supposed to only demonstrate that the inferred types can be successfully loaded and used in ArkAnalyzer.
