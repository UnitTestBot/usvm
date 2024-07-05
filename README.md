## Synthetic Benchmark Generation using Mutation Fuzzing

This project aims to generate synthetic benchmarks using the mutation fuzzing method. 
Synthetic benchmarks are essential for testing the SAST tools. 
By employing mutation fuzzing, this project generates diverse test cases by mutating existing benchmarks or code snippets.

## Get started
1. Clone vulnomicon [repo](https://github.com/flawgarden/vulnomicon)
2. Set up benchmark according to instructions 
3. Run desired tools with standard configuration
4. Make copy of benchmark with "-fuzz" suffix, for example if benchmark name is "BenchmarkJava" make copy in directory "BenchmarkJava-fuzz" (do not forget to adapt running script to this copy)
5. Remove unneeded sources to speedup process (be careful, do not remove classes, which can be used by mutants)
6. Execute markup task and specify path to tools_truth.sarif to "[BenchmarkName]-fuzz" directory: `/gradlew markupBenchmark -Dorg.gradle.java.home=[PATH_TO_JAVA_17] -PprogramArgs="-pathToFuzzBench [PATH_TO_FUZZ_COPY] -tools [PATH_TO_TOOL_RESULTS]"`
7. Now you can use our tool

## Usage

To use this tool for synthetic benchmark generation, use next commands:\

`./gradlew runFuzzer -Dorg.gradle.java.home=[PATH_TO_JDK_21] -PprogramArgs="-pathToBench [PATH_TO_ORIGINAL_BENCHMARK] -pathToFuzzBench [PATH_TO_CLONED_BENCHMARK_TO_FUZZ] -pathToScript [PATH_TO_SCRIPT_TO_EXECUTE_FUZZ_BENCHMARK] -pathToVuln [PATH_TO_VULNOMICON] -n [BATCH_SIZE] [-s] [-nm [NUM]] [-nf [NUM]]" -PvulnomiconJavaHome=[PATH_TO_JAVA_17]`
* runFuzzer/runInfFuzzer - commands to run fuzzing for one cycle (generate mutants -> execute -> finish) or infinite
* -n - number of mutants to estimate (recommended: ~500)
* -nm - number of successful mutations to make final version of mutant (default 2)
* -nf - Number of generated mutants for file (default 5). Amount of final test cases will be `BATCH_SIZE * NF`
* -s - flag to execute tool in **results SORTING mode** (may take a lot of time)

To check templates use command: 
`./gradlew -Dorg.gradle.java.home=[PATH_TO_JDK_21] checkTemplates`

If you want to specify file with templates and even index you can use args:
`./gradlew -Dorg.gradle.java.home=[PATH_TO_JDK_21] checkTemplates -PprogramArgs="[file_name] [index_of_template]"`

For detailed information on the templates used for benchmark generation, refer to the [documentation](docs/templates.md) on templates.

## Contributing

Contributions are welcome! If you have ideas for improvements or encounter any issues, please open an issue or submit a pull request on GitHub.