## Synthetic Benchmark Generation using Mutation Fuzzing

This project aims to generate synthetic benchmarks using the mutation fuzzing method. 
Synthetic benchmarks are essential for testing the SAST tools. 
By employing mutation fuzzing, this project generates diverse test cases by mutating existing benchmarks or code snippets.

## Usage

To use this tool for synthetic benchmark generation, use next commands:\
`./gradlew [runFuzzer/runInfFuzzer] -PprogramArgs="-d [PATH_TO_BENCHMARK_TEMPLATE] -n [BATCH_SIZE] [-l] [-s]"`
* runFuzzer/runInfFuzzer - commands to run fuzzing for one cycle (generate mutants -> execute -> finish) or infinite
* PATH_TO_BENCHMARK_TEMPLATE - path to template of Benchmark, which will be used to save mutants and run experiments
* BATCH_SIZE - number of mutants to estimate (recommended: ~500)
* -l - flag, indicates, if SAST estimation should be local or server-based
* -s - flag to execute tool in **results SORTING mode** (may take a lot of time)

To check templates use command: 
`./gradlew checkTemplates`

If you want to specify file with templates and even index you can use args:
`./gradlew checkTemplates -PprogramArgs="[file_name] [index_of_template]"`

For detailed information on the templates used for benchmark generation, refer to the [documentation](docs/templates.md) on templates.

## Contributing

Contributions are welcome! If you have ideas for improvements or encounter any issues, please open an issue or submit a pull request on GitHub.