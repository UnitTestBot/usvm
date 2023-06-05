#define HANDLERS_DEFS \
    jmethodID handle_instruction; \
    jmethodID handle_load_const_long; \
    jmethodID handle_fork; \
    jmethodID handle_gt_long; \
    jmethodID handle_lt_long; \
    jmethodID handle_eq_long; \
    jmethodID handle_ne_long; \
    jmethodID handle_le_long; \
    jmethodID handle_ge_long; \
    jmethodID handle_add_long; \
    jmethodID handle_sub_long; \
    jmethodID handle_mul_long; \
    jmethodID handle_div_long; \
    jmethodID handle_rem_long;

#define handle_name_instruction "handlerInstruction"
#define handle_sig_instruction  "(Lorg/usvm/interpreter/ConcolicRunContext;I)V"

#define handle_name_load_const_long "handlerLoadConstLong"
#define handle_sig_load_const_long  "(Lorg/usvm/interpreter/ConcolicRunContext;J)Lorg/usvm/language/Symbol;"

#define handle_name_fork "handlerFork"
#define handle_sig_fork  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;)V"

#define handle_name_gt_long "handlerGTLong"
#define handle_sig_gt_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_lt_long "handlerLTLong"
#define handle_sig_lt_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_eq_long "handlerEQLong"
#define handle_sig_eq_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_ne_long "handlerNELong"
#define handle_sig_ne_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_le_long "handlerLELong"
#define handle_sig_le_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_ge_long "handlerGELong"
#define handle_sig_ge_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_add_long "handlerADDLong"
#define handle_sig_add_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_sub_long "handlerSUBLong"
#define handle_sig_sub_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_mul_long "handlerMULLong"
#define handle_sig_mul_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_div_long "handlerDIVLong"
#define handle_sig_div_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"

#define handle_name_rem_long "handlerREMLong"
#define handle_sig_rem_long  "(Lorg/usvm/interpreter/ConcolicRunContext;Lorg/usvm/language/Symbol;Lorg/usvm/language/Symbol;)Lorg/usvm/language/Symbol;"
