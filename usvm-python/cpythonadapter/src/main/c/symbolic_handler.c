#include "symbolic_handler.h"
#include "utils.h"

#define CHECK_FOR_EXCEPTION(fail_value) \
    if ((*ctx->env)->ExceptionCheck(ctx->env)) { \
        /*printf("HERE\n"); \
        fflush(stdout);*/ \
        PyErr_SetString(PyExc_RuntimeError, "Java exception"); \
        return fail_value; \
    }

#define CALL_JAVA_METHOD(result, ctx, func, args...) \
    result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_##func, args); \
    CHECK_FOR_EXCEPTION(Py_None)

#define BINARY_INT_HANDLER(func) \
    PyObject *left = args[0], *right = args[1]; \
    if (!is_wrapped_java_object(left) || !is_wrapped_java_object(right)) \
        return Py_None; \
    jobject left_obj = ((JavaPythonObject *) left)->object; \
    jobject right_obj = ((JavaPythonObject *) right)->object; \
    jobject result; \
    CALL_JAVA_METHOD(result, ctx, func, ctx->context, signal_id, left_obj, right_obj) \
    if (!result) \
        return Py_None; \
    PyObject *r = wrap_java_object(ctx->env, result); \
    return r;


PyObject *
handler(int signal_type, int signal_id, int nargs, PyObject *const *args, void *param) {
    ConcolicContext *ctx = (ConcolicContext *) param;

    if (signal_id == SYM_EVENT_ID_CONST) {
        assert(signal_type == SYM_EVENT_TYPE_STACK && nargs == 1);

        PyObject *value = args[0];
        if (!PyLong_Check(value))
            return Py_None;
        int overflow;
        long value_as_long = PyLong_AsLongAndOverflow(value, &overflow);
        if (overflow)
            return Py_None;

        jobject result;
        CALL_JAVA_METHOD(result, ctx, load_const_long, ctx->context, value_as_long)
        return PyTuple_Pack(1, wrap_java_object(ctx->env, result));

    } else if (signal_id == SYM_EVENT_ID_FORK) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);

        PyObject *value = args[0];
        if (is_wrapped_java_object(value)) {
            //printf("Fork on known condition\n");
            //fflush(stdout);
            jobject obj = ((JavaPythonObject *) value)->object;
            (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_fork, ctx->context, obj);
            CHECK_FOR_EXCEPTION(1)
        }

        return Py_None;

    } else if (signal_id == SYM_EVENT_ID_INT_GT) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(gt_long)

    } else if (signal_id == SYM_EVENT_ID_INT_LT) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(lt_long)

    } else if (signal_id == SYM_EVENT_ID_INT_EQ) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(eq_long)

    } else if (signal_id == SYM_EVENT_ID_INT_NE) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(ne_long)

    } else if (signal_id == SYM_EVENT_ID_INT_LE) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(le_long)

    } else if (signal_id == SYM_EVENT_ID_INT_GE) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(ge_long)

    } else if (signal_id == SYM_EVENT_ID_INT_ADD) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(add_long)

    } else if (signal_id == SYM_EVENT_ID_INT_SUB) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(sub_long)

    } else if (signal_id == SYM_EVENT_ID_INT_MULT) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(mul_long)

    } else if (signal_id == SYM_EVENT_ID_INT_FLOORDIV) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(div_long)

    } else if (signal_id == SYM_EVENT_ID_INT_REM) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_INT_HANDLER(rem_long)

    } else if (signal_id == SYM_EVENT_ID_INT_POW) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 3);
        if (args[2] != Py_None)
            return Py_None;
        BINARY_INT_HANDLER(pow_long)

    } else if (signal_id == SYM_EVENT_ID_INSTRUCTION) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);

        PyFrameObject *frame = args[0];
        int instruction = take_instruction_from_frame(frame);
        (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_instruction, ctx->context, instruction);
        CHECK_FOR_EXCEPTION(1)

        return Py_None;
    }

    return Py_None;
}