../gradlew -PcpythonActivated=true :usvm-python:cpythonadapter:CPythonClean &&\
../gradlew -PcpythonActivated=true :usvm-python:cpythonadapter:CPythonDistclean &&\
../gradlew -PcpythonActivated=true :usvm-python:cpythonadapter:clean &&\
../gradlew -PcpythonActivated=true :usvm-python:clean &&\
echo "false" > cpythonadapter/include_pip_in_build &&\
../gradlew -PcpythonActivated=true :usvm-python:cpythonadapter:linkDebug &&\
../gradlew -PcpythonActivated=true :usvm-util:jar &&\
../gradlew -PcpythonActivated=true :usvm-core:jar &&\
../gradlew -PcpythonActivated=true :usvm-python:jar &&\
../gradlew -PcpythonActivated=true :usvm-python:distZip &&\
echo "true" > cpythonadapter/include_pip_in_build