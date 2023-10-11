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
construct_symbolic_method_with_self(JNIEnv *env, jobject symbolic_self, call_type call) {
    assert(methods_holder);
    SymbolicMethod *result = malloc(sizeof(SymbolicMethod));
    result->call = call;
    result->self_reference = create_global_ref(env, symbolic_self);
    add_ref_to_list(&methods_holder, result);
    return result;
}

PyObject *
call_symbolic_method(SymbolicMethod *method, ConcolicContext *ctx, PyObject *args, PyObject *kwargs) {
    if (kwargs)
        return Py_None;  // TODO
    return method->call(ctx, method->self_reference, args);
}