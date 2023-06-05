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

    jobject result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_load_const_long, ctx->context, value_as_long);
    return wrap_java_object(ctx->env, result);
}

static void
handle_fork(ConcolicContext *ctx, PyObject *value) {
    if (!is_wrapped_java_object(value))
        return;
    //printf("Fork on known condition\n");
    //fflush(stdout);
    jobject obj = ((JavaPythonObject *) value)->object;
    (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_fork, ctx->context, obj);
}

#define BINARY_INT_HANDLER(func) \
    if (!is_wrapped_java_object(left) || !is_wrapped_java_object(right)) \
        return Py_None; \
    jobject left_obj = ((JavaPythonObject *) left)->object; \
    jobject right_obj = ((JavaPythonObject *) right)->object; \
    jobject result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_##func, ctx->context, left_obj, right_obj); \
    if (!result) \
        return 0; \
    PyObject *r = wrap_java_object(ctx->env, result); \
    return r;

static PyObject *
handle_int_gt(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(gt_long)
}

static PyObject *
handle_int_lt(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(lt_long)
}

static PyObject *
handle_int_eq(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(eq_long)
}

static PyObject *
handle_int_ne(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(ne_long)
}

static PyObject *
handle_int_ge(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(ge_long)
}

static PyObject *
handle_int_le(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(le_long)
}

static PyObject *
handle_int_add(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(add_long)
}

static PyObject *
handle_int_sub(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(sub_long)
}

static PyObject *
handle_int_mul(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(mul_long)
}

static PyObject *
handle_int_div(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(div_long)
}

static PyObject *
handle_int_rem(ConcolicContext *ctx, PyObject *left, PyObject *right) {
    BINARY_INT_HANDLER(rem_long)
}

static void
handle_instruction(ConcolicContext *ctx, PyObject *frame) {
    int instruction = take_instruction_from_frame(frame);
    (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_instruction, ctx->context, instruction);
}

PyObject *
handler(int signal_type, int signal_id, int nargs, PyObject *const *args, void *param) {
    ConcolicContext *ctx = (ConcolicContext *) param;

    if (signal_id == SYM_EVENT_ID_CONST) {
        assert(signal_type == SYM_EVENT_TYPE_STACK && nargs == 1);
        PyObject *result = load_const(ctx, args[0]);
        if (result && result != Py_None)
            return PyTuple_Pack(1, result);
        return Py_None;

    } else if (signal_id == SYM_EVENT_ID_FORK) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);
        handle_fork(ctx, args[0]);
        return Py_None;

    } else if (signal_id == SYM_EVENT_ID_INT_GT) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_gt(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_LT) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_lt(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_EQ) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_eq(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_NE) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_ne(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_LE) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_le(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_GE) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_ge(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_ADD) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_add(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_SUB) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_sub(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_MULT) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_mul(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_FLOORDIV) {
            assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
            return handle_int_div(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INT_REM) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        return handle_int_rem(ctx, args[0], args[1]);

    } else if (signal_id == SYM_EVENT_ID_INSTRUCTION) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);
        handle_instruction(ctx, args[0]);
        return Py_None;
    }

    return Py_None;
}