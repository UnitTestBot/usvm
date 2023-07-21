#include "symbolic_handler.h"
#include "utils.h"
#include "virtual_objects.h"

#define BINARY_METHOD_HANDLER(func) \
    PyObject *left = args[0], *right = args[1]; \
    if (!is_wrapped_java_object(left) || !is_wrapped_java_object(right)) \
        return Py_None; \
    jobject left_obj = ((JavaPythonObject *) left)->reference; \
    jobject right_obj = ((JavaPythonObject *) right)->reference; \
    jobject result; \
    CALL_JAVA_METHOD(result, ctx, func, ctx->context, signal_id, left_obj, right_obj) \
    if (!result) \
        return Py_None; \
    PyObject *r = wrap_java_object(ctx->env, result); \
    return r;

static jobject
make_load_const(ConcolicContext *ctx, PyObject *value) {
    if (PyLong_Check(value)) {
        int overflow;
        long value_as_long = PyLong_AsLongAndOverflow(value, &overflow);
        if (overflow)
            return 0;

        jobject result;
        CALL_JAVA_METHOD_CUSTOM_FAIL(0, result, ctx, load_const_long, ctx->context, value_as_long)

        return result;

    } else if (PyTuple_Check(value)) {

        int n = PyTuple_GET_SIZE(value);
        jobjectArray args = (*ctx->env)->NewObjectArray(ctx->env, n, ctx->symbol_cls, 0);

        for (int i = 0; i < n; i++) {
            jobject elem = make_load_const(ctx, PyTuple_GetItem(value, i));
            if (!elem) {
                return 0;
            }
            (*ctx->env)->SetObjectArrayElement(ctx->env, args, i, elem);
        }

        jobject result;
        CALL_JAVA_METHOD_CUSTOM_FAIL(0, result, ctx, load_const_tuple, ctx->context, args)

        return result;

    } else {
        return 0;
    }
}

PyObject *
handler(int signal_type, int signal_id, int nargs, PyObject *const *args, void *param) {
    ConcolicContext *ctx = (ConcolicContext *) param;

    if (signal_id == SYM_EVENT_ID_CONST) {
        assert(signal_type == SYM_EVENT_TYPE_STACK && nargs == 1);
        PyObject *value = args[0];
        jobject result = make_load_const(ctx, value);
        if (!result)
            return Py_None;

        return PyTuple_Pack(1, wrap_java_object(ctx->env, result));

    } else if (signal_id == SYM_EVENT_ID_FORK) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);

        PyObject *value = args[0];
        if (is_wrapped_java_object(value)) {
            //printf("Fork on known condition\n");
            //fflush(stdout);
            jobject obj = ((JavaPythonObject *) value)->reference;
            (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_fork, ctx->context, obj);
            CHECK_FOR_EXCEPTION(ctx, (PyObject *) 1)
        }

        return Py_None;

    } else if (signal_id == SYM_EVENT_ID_INT_GT) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(gt_long)

    } else if (signal_id == SYM_EVENT_ID_INT_LT) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(lt_long)

    } else if (signal_id == SYM_EVENT_ID_INT_EQ) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(eq_long)

    } else if (signal_id == SYM_EVENT_ID_INT_NE) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(ne_long)

    } else if (signal_id == SYM_EVENT_ID_INT_LE) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(le_long)

    } else if (signal_id == SYM_EVENT_ID_INT_GE) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(ge_long)

    } else if (signal_id == SYM_EVENT_ID_INT_ADD) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(add_long)

    } else if (signal_id == SYM_EVENT_ID_INT_SUB) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(sub_long)

    } else if (signal_id == SYM_EVENT_ID_INT_MULT) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(mul_long)

    } else if (signal_id == SYM_EVENT_ID_INT_FLOORDIV) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(div_long)

    } else if (signal_id == SYM_EVENT_ID_INT_REM) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(rem_long)

    } else if (signal_id == SYM_EVENT_ID_INT_POW) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 3);
        if (args[2] != Py_None)
            return Py_None;
        BINARY_METHOD_HANDLER(pow_long)

    } else if (signal_id == SYM_EVENT_ID_INSTRUCTION) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);

        PyFrameObject *frame = (PyFrameObject *) args[0];
        int instruction = take_instruction_from_frame(frame);
        (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_instruction, ctx->context, instruction);
        CHECK_FOR_EXCEPTION(ctx, (PyObject *) 1)

        return Py_None;

    } else if (signal_id == SYM_EVENT_ID_CREATE_LIST) {
        assert(signal_type == SYM_EVENT_TYPE_STACK && nargs >= 0);

        //printf("BUILD_LIST!\n");
        //fflush(stdout);

        jobjectArray symbol_array = (*ctx->env)->NewObjectArray(ctx->env, nargs, ctx->symbol_cls, 0);
        for (int i = 0; i < nargs; i++) {
          if (!is_wrapped_java_object(args[i]))
              return Py_None;

          jobject elem = ((JavaPythonObject *) args[i])->reference;
          (*ctx->env)->SetObjectArrayElement(ctx->env, symbol_array, i, elem);
        }

        jobject result;
        CALL_JAVA_METHOD(result, ctx, create_list, ctx->context, symbol_array);
        return PyTuple_Pack(1, wrap_java_object(ctx->env, result));

    } else if (signal_id == SYM_EVENT_ID_LIST_GET_ITEM) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(list_get_item)

    } else if (signal_id == SYM_EVENT_ID_LIST_SET_ITEM) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 3);

        if (!is_wrapped_java_object(args[0]) || !is_wrapped_java_object(args[1]) || !is_wrapped_java_object(args[2]))
           return Py_None;

        jobject self = ((JavaPythonObject *) args[0])->reference;
        jobject o1 = ((JavaPythonObject *) args[1])->reference;
        jobject o2 = ((JavaPythonObject *) args[2])->reference;

        (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_list_set_item, ctx->context, signal_id, self, o1, o2);
        CHECK_FOR_EXCEPTION(ctx, (PyObject *) 1)

    } else if (signal_id == SYM_EVENT_ID_LIST_EXTEND) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(list_extend)

    } else if (signal_id == SYM_EVENT_ID_LIST_APPEND) {
        assert(signal_type == SYM_EVENT_TYPE_METHOD && nargs == 2);
        BINARY_METHOD_HANDLER(list_append)

    } else if (signal_id == SYM_EVENT_ID_PYTHON_FUNCTION_CALL) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);
        // TODO

    } else if (signal_id == SYM_EVENT_ID_RETURN) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 0);
        // TODO

    } else if (signal_id == SYM_EVENT_ID_FORK_RESULT) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);

        (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_fork_result, ctx->context, args[0] == Py_True);
        CHECK_FOR_EXCEPTION(ctx, (PyObject *) 1)

    } else if (signal_id == SYM_EVENT_ID_NB_BOOL) {
        assert(signal_type == SYM_EVENT_TYPE_NOTIFY && nargs == 1);

        PyObject *symbolic = args[0];
        if (is_wrapped_java_object(symbolic)) {
            jobject object = ((JavaPythonObject *) symbolic)->reference;
            (*ctx->env)->CallStaticVoidMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_nb_bool, ctx->context, object);
            CHECK_FOR_EXCEPTION(ctx, (PyObject *) 1)
        }

    }

    return Py_None;
}