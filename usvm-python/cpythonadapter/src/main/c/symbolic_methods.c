#include "symbolic_methods.h"
#include "utils.h"
#include "approximations.h"
#include "converters.h"

RefHolderNode methods_holder_root = {
    NULL,
    NULL
};

RefHolderNode *methods_holder = &methods_holder_root;

static void
release_method(void *address, void *data) {
    free((SymbolicMethod *) address);
}

void clean_methods() {
    clean_list(&methods_holder, NULL, release_method);
}

SymbolicMethod *
construct_list_append_method(JNIEnv *env, jobject symbolic_self) {
    assert(methods_holder);
    SymbolicMethod *result = malloc(sizeof(SymbolicMethod));
    result->call = SymbolicMethod_list_append;
    result->self_reference = create_global_ref(env, symbolic_self);
    add_ref_to_list(&methods_holder, result);
    return result;
}

SymbolicMethod *
construct_symbolic_method_without_self(call_type call) {
    assert(methods_holder);
    SymbolicMethod *result = malloc(sizeof(SymbolicMethod));
    result->call = call;
    result->self_reference = 0;
    add_ref_to_list(&methods_holder, result);
    return result;
}

PyObject *
call_symbolic_method(SymbolicMethod *method, SymbolicAdapter *adapter, PyObject *args, PyObject *kwargs) {
    return method->call(adapter, method->self_reference, args, kwargs);
}