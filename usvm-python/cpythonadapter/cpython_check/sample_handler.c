#include "Python.h"
#include "symbolicadapter.h"

#define PROGRAM \
    "def longest_subsequence(array: list[int]) -> list[int]:  # This function is recursive\n"\
    "     array_length = len(array)\n"\
    "     if array_length <= 1:\n"\
    "         return array\n"\
    "     pivot = array[0]\n"\
    "     isFound = False\n"\
    "     i = 1\n"\
    "     longest_subseq = []\n"\
    "     while not isFound and i < array_length:\n"\
    "         if array[i] < pivot:\n"\
    "             isFound = True\n"\
    "             temp_array = [element for element in array[i:] if element >= array[i]]\n"\
    "             temp_array = longest_subsequence(temp_array)\n"\
    "             if len(temp_array) > len(longest_subseq):\n"\
    "                 longest_subseq = temp_array\n"\
    "         else:\n"\
    "             i += 1\n"\
    "     temp_array = [element for element in array[1:] if element >= pivot]\n"\
    "     temp_array = [pivot] + longest_subsequence(temp_array)\n"\
    "     if len(temp_array) > len(longest_subseq):\n"\
    "         return temp_array\n"\
    "     else:\n"\
    "         return longest_subseq\n"\



int main() {
    Py_Initialize();

    PyObject *dict = PyDict_New();
    PyRun_StringFlags(PROGRAM, Py_file_input, dict, dict, 0);

    if (PyErr_Occurred()) {
        PyErr_Print();
        return 1;
    }

    PyObject *function = PyRun_StringFlags("longest_subsequence", Py_eval_input, dict, dict, 0);
    printf("Function: %p\n", function); fflush(stdout);
    PyObject *arg = PyTuple_Pack(2, PyLong_FromLong(0), PyLong_FromLong(0));
    PyObject *arg_packed = PyTuple_New(2);
    PyTuple_SetItem(arg_packed, 0, arg);
    PyTuple_SetItem(arg_packed, 1, Py_None);
    PyObject *args[] = {arg_packed};

    SymbolicAdapter *adapter = create_new_adapter(0);
    PyObject *result = SymbolicAdapter_run((PyObject *) adapter, function, 1, args);

    if (result == NULL) {
        PyErr_Print();
    }

    Py_FinalizeEx();
}