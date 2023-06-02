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
    if (!is_wrapped_java_object(value))
        return;
    printf("Fork on known condition\n");
    fflush(stdout);
    jobject obj = ((JavaPythonObject *) value)->object;
    (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_fork, ctx->context, obj);
}

static void
handle_fork_result(ConcolicContext *ctx, PyObject *value) {
    if (!PyBool_Check(value))
        return;
    int result = value == Py_True;
    (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_fork_result, ctx->context, result);
}

static PyObject *
handle_gt(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    if (!is_wrapped_java_object(left) || !is_wrapped_java_object(right))
        return Py_None;
    jobject left_obj = ((JavaPythonObject *) left)->object;
    jobject right_obj = ((JavaPythonObject *) right)->object;
    jobject result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_gt_long, ctx->context, left_obj, right_obj);
    PyObject *r = wrap_java_object(ctx->env, result);
    return r;
}

static void
handle_instruction(ConcolicContext *ctx, PyObject *frame) {
    int instruction = take_instruction_from_frame(frame);
}

PyObject *
handler(int signal_type, int signal_id, int nargs, PyObject *const *args, void *param) {
    ConcolicContext *ctx = (ConcolicContext *) param;

    if (signal_id == SYM_EVENT_ID_CONST) {
        assert(signal_type == SYM_EVENT_TYPE_STACK && nargs == 1);
        return PyTuple_Pack(1, load_const(ctx, args[0]));

    } else if (signal_id == SYM_EVENT_ID_FORK) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);
        handle_fork(ctx, args[0]);
        return Py_None;

    } else if (signal_id == SYM_EVENT_ID_FORK_RESULT) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);
        printf("FORK RESULT: %d %s\n", args[0] == Py_True, Py_TYPE(args[0])->tp_name);
        fflush(stdout);
        handle_fork_result(ctx, args[0]);
        return Py_None;

    } else if (signal_id == SYM_EVENT_ID_GT) {
        assert(signal_type == SYM_EVENT_TYPE_STACK && nargs == 2);
        //printf("GT\n");
        //fflush(stdout);
        return PyTuple_Pack(1, handle_gt(ctx, args[0], args[1]));  // TODO

    } else if (signal_id == SYM_EVENT_ID_INSTRUCTION) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);
        handle_instruction(ctx, args[0]);
        return Py_None;
    }

    return Py_None;
}