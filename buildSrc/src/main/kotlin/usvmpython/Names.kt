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

/** Task names */
const val CPYTHON_BUILD_DEBUG_CONFIGURATION = "CPythonBuildConfigurationDebug"
const val CPYTHON_BUILD_DEBUG = "CPythonBuildDebug"
const val INSTALL_MYPY_RUNNER_TASK = "installUtbotMypyRunner"
const val BUILD_SAMPLES_TASK = "buildSamples"

/** Property names */
const val PROPERTY_FOR_CPYTHON_ACTIVATION = "cpythonActivated"

/** Entry points */
const val BUILD_SAMPLES_ENTRY_POINT = "org.usvm.runner.BuildSamplesKt"