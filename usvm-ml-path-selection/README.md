## Machine Learning Path Selector

### Entry point

To run tests with this path selector use `jarRunner.kt`. You can pass a path to a configuration json as the first argument. Gathered statistics will be put in a folder according to your configuration.

### Config

A config object is declared inside `MLConfig.kt`. A detailed description of all the options is listed below:

- `gameEnvPath` - a path to a folder that contains trained models (`rnn_cell.onnx`, `gnn_model.onnx`, `actor_model.onnx`) and a blacklist of tests to be skipped (`blacklist.txt`), also some logs are saved to this folder
- `dataPath` - a path to a folder to save all statistics into
- `defaultAlgorithm` - an algorithm to use if a trained model is not found, must be one of: `BFS`, `ForkDepthRandom`
- `postprocessing` - how actor model's outputs should be processed, must be one of: `Argmax` (choose an id of the maximum value), `Softmax` (sample from a distribution derived from the outputs via the softmax), `None` (sample from the outputs — only when they form a distribution)
- `mode` - a mode for `jarRunner.kt`, must be one of: `Calculation` (to calculate statistics used to train models), `Aggregation` (to aggregate statistics for different tests into one file), `Both` (to both calculate statistics and aggregate them), `Test` (to test this path selector with different time limits and compare it to other path selectors)
- `logFeatures` - whether to save statistics used to train models
- `shuffleTests` - whether to shuffle tests before running (affects the tests being run if the `dataConsumption` option is less than 100)
- `discounts` - time discounts used when testing path selectors
- `inputShape` - an input shape of an actor model
- `maxAttentionLength` - a maximum attention length of a PPO actor model
- `useGnn` - whether to use a GNN model
- `dataConsumption` - a percentage of tests to run
- `hardTimeLimit` - a time limit for one test
- `solverTimeLimit` - a time limit for one solver call
- `maxConcurrency` - a maximum number of threads running different tests concurrently
- `graphUpdate` - when to update block graph data, must be one of: `Once` (at the beginning of a test), `TestGeneration` (every time a new test is generated)
- `logGraphFeatuers` - whether to save graph statistics used to train a GNN model to a dataset file
- `gnnFeaturesCount` - a number of features that a GNN model returns
- `useRnn` - whether to use an RNN model
- `rnnStateShape` - a shape of an RNN state
- `rnnFeaturesCount` - a number of features that an RNN model returns
- `inputJars` - jars and their packages to run tests on

### How to modify the metric

To modify the metric you may change values of the `reward` property of the `ActionData` objects. They are written inside the property `path` of the `FeaturesLoggingPathSelector`. Currently, the metric is calculated in the `remove` method of the `FeaturesLoggingPathSelector`.

### Training environment

The training environment and its description are inside `environment.zip`.

### "Other" files

Source files which names start with "Other" are modified copies of files from other modules. They were modified to support this path selector.
