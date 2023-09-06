## Machine Learning Path Selector

### Entry point

To run tests with this path selector and gather statistics, use `jarRunner.kt`. It takes a json with options as an input.

### Config

The config object is declared inside the `MLConfig.kt` file, here is a detailed description of all the options:

- `gameEnvPath` - path to the folder which contains trained models (`rnn_cell.onnx`, `gnn_model.onnx`, `actor_model.onnx`) and a blacklist of tests that should be skipped (`blacklist.txt`), also some logs are saved here
- `dataPath` - path to the folder into which all statistics are saved
- `defaultAlgorithm` - algorithm to use if no trained model is found, must be `BFS` or `ForkDepthRandom`
- `postprocessing` - how an actor model's outputs should be processed, must be `Argmax` (for PI), `Softmax` (for PPO) or `None` (if the model returns probabilities)
- `mode` - mode in which to run `jarRunner.kt`, must be `Calculation` (to calculate statistics used for training models), `Aggregation` (to aggregate statistics from different tests into one file), `Both` (to both calculate statistics and aggregate them) or `Test` (to test this path selector with different time limits and compare it to other path selectors)
- `logFeatures` - whether to save statistics used for training models
- `shuffleTests` - whether to run tests in a random order
- `discounts` - discounts used when testing path selectors
- `inputShape` - input shape of an actor model
- `maxAttentionLength` - maximum attention length of a PPO actor model
- `useGnn` - whether to use a GNN model
- `dataConsumption` - percentage of tests to run
- `hardTimeLimit` - time limit for one test
- `solverTimeLimit` - time limit for one solver call
- `maxConcurrency` - maximum number of threads running different tests concurrently
- `graphUpdate` - when to update block graph data, must be `Once` (at the beginning of a test) or `TestGeneration` (every time a new test was generated)
- `logGraphFeatuers` - whether to save graph statistics used for training a GNN model
- `gnnFeaturesCount` - number of features that a GNN model returns
- `useRnn` - whether to use an RNN model
- `rnnStateShape` - shape of an RNN state
- `rnnFeaturesCount` - number of features that an RNN model returns
- `inputJars` - jars and their packages for which to run tests

### Metrics modification

To change metrics you can change the values of the `reward` property of `ActionData` objects in the property `path` in `FeaturesLoggingPathSelector`. Right now it is calculated in the `remove` method.

### Training environment

The training environment and its description is inside of `environment.zip`.

### "Other" files

Source files which names start with "Other" are copies of files from other modules that were modified to support this path selector.