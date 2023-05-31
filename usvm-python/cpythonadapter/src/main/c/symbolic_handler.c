#include "symbolic_handler.h"
#include "utils.h"

static PyObject *
load_const(ConcolicContext *ctx, PyObject *value) {
    if (!PyLong_Check(value))
        return Py_None;
    int overflow;
    long value_as_long = PyLong_AsLongAndOverflow(value, &overflow);
    if (overflow)
        return Py_None;

    jobject result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->load_const_long, ctx->context, value_as_long);
    return wrap_java_object(ctx->env, result);
}

static void
handle_fork(ConcolicContext *ctx, PyObject *value) {
    //printf("HERE 1\n");
    //fflush(stdout);
    if (!is_wrapped_java_object(value))
        return;
    jobject obj = ((JavaPythonObject *) value)->object;
    //printf("HERE\n");
    //fflush(stdout);
    (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_fork, ctx->context, obj);
}

static void
handle_fork_result(ConcolicContext *ctx, PyObject *value) {
    if (!PyBool_Check(value))
        return;
    int result = value == Py_True;
    (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_fork_result, ctx->context, result);
}

PyObject *
handler(int signal_type, int signal_id, int nargs, PyObject *const *args, void *param) {
    ConcolicContext *ctx = (ConcolicContext *) param;

    //printf("IN HANDLER. type: %d, id: %d\n", signal_type, signal_id);
    //fflush(stdout);

    if (signal_id == SYM_EVENT_ID_CONST) {
        assert(signal_type == SYM_EVENT_TYPE_STACK && nargs == 1);
        return PyTuple_Pack(1, load_const(ctx, args[0]));
    } else if (signal_id == SYM_EVENT_ID_FORK) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);
        handle_fork(ctx, args[0]);
    } else if (signal_id == SYM_EVENT_ID_FORK_RESULT) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);
        //printf("FORK RESULT: %d\n", args[0] == Py_True);
        //fflush(stdout);
        handle_fork_result(ctx, args[0]);
    }

    return Py_None;
}