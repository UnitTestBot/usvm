../gradlew :usvm-python:cpythonadapter:CPythonClean &&\
../gradlew :usvm-python:cpythonadapter:CPythonDistclean &&\
../gradlew :usvm-python:cpythonadapter:clean &&\
../gradlew :usvm-python:clean &&\
echo "false" > cpythonadapter/include_pip_in_build &&\
../gradlew :usvm-python:cpythonadapter:linkDebug &&\
../gradlew :usvm-core:jar &&\
../gradlew :usvm-python:jar &&\
../gradlew :usvm-python:distZip &&\
echo "true" > cpythonadapter/include_pip_in_build