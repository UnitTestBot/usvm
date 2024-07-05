package usvmpython

/** Module names */
const val USVM_PYTHON_MODULE = "usvm-python"
const val CPYTHON_ADAPTER_MODULE = "usvm-python:cpythonadapter"
const val USVM_PYTHON_ANNOTATIONS_MODULE = "usvm-python:usvm-python-annotations"
const val USVM_PYTHON_MAIN_MODULE = "usvm-python:usvm-python-main"
const val USVM_PYTHON_RUNNER_MODULE = "usvm-python:usvm-python-runner"
const val USVM_PYTHON_COMMONS_MODULE = "usvm-python:usvm-python-commons"

/** Task group names */
const val CPYTHON_GROUP_NAME = "cpython"
const val SAMPLE_GROUP_NAME = "samples"
const val MANUAL_RUN_GROUP_NAME = "run"

/** Task names */
const val CPYTHON_BUILD_DEBUG_CONFIGURATION = "CPythonBuildConfigurationDebug"
const val CPYTHON_BUILD_DEBUG = "CPythonBuildDebug"
const val INSTALL_MYPY_RUNNER_TASK = "installUtbotMypyRunner"
const val BUILD_SAMPLES_TASK = "buildSamples"
const val MANUAL_TEST_FOR_RUNNER = "manualTestOfRunner"
const val MANUAL_TEST_DEBUG_TASK = "manualTestDebug"
const val MANUAL_TEST_DEBUG_NO_LOGS_TASK = "manualTestDebugNoLogs"

/** Property names */
const val PROPERTY_FOR_CPYTHON_ACTIVATION = "cpythonActivated"
const val PROPERTY_FOR_CPYTHON_SSL_PATH = "cpython.ssl.path"

/** Entry points */
const val BUILD_SAMPLES_ENTRY_POINT = "org.usvm.runner.BuildSamplesKt"
const val MANUAL_TEST_FOR_RUNNER_ENTRY = "org.usvm.runner.ManualTestKt"
const val RUNNER_ENTRY_POINT = "org.usvm.runner.UtBotPythonRunnerEntryPointKt"
const val MANUAL_TEST_ENTRY = "org.usvm.runner.manual.ManualTestKt"

const val CPYTHON_ADAPTER_CLASS = "org.usvm.interpreter.CPythonAdapter"