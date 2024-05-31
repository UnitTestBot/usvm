## General Documentation for Code Templates
### Introduction

Code templates are predefined structures that help automate the process of SAST-fuzzing. 
This documentation provides general principles and rules for working with code templates.
Code templates inserts in the random place of source test sample and fills with variables/generated expressions available in scope.
The main feature of the templates is the presence of **typed placeholders**.

### Structure of Templates

Each template consists of the following main components:

1) Additional Classes: These are supplementary classes that are included to support the main functionality of the template.
**It should not contain any typed placeholder!**
```   
~class full_class_name start~
CLASS_BODY
~class full_class_name end~ 
```
2) Main template body
```
~main class start~
TEMPLATE BODY
~main class end~
```
Which contains:
* List of Imports: Specifies the libraries and packages that need to be imported for the template to work correctly.
```
~import java.util.*;~
```
* Templates bodies (min 1):
```
~template start~
TEMPLATE BODY
~template end~
```
Each of which consists of:
* List of Additional Functions: Contains any helper functions or methods that are required by the main template logic.
**It also should not contain any typed placeholder!**
```
~function fun_name start~
FUNCTION BODY
~function fun_name end~
```
* Main Template Code: The primary code constructs that will be used in code generation, for example:
```
if (~[EXPR_boolean]~) {
    ~[VAR_String]~ = ~[EXPR_String]~;
}
```

### Templates syntax description
Templates contain special markers that indicate where specific values or expressions need to be inserted. Here are some common markers:
* **\~[EXPR_Type]\~**: Expression of type **Type** (Type can be replaced by any type in the program). 
An expression can be either a program variable or a generated expression using variables from the program. 
For example, if we need to fill **~[EXPR_Boolean]~** placeholder, and we have `String s` in the scope, we can generate
`s.isEmpty()`

* **\~[VAR_Type]\~**: Available from scope variable of type **Type**
* **\~[CONST_Type]\~**: Generated constant of type **Type** (currently supports *Collection*, *ArrayList*, *primitives* and *String*)
* **\~[TYPE]\~**: Randomly generated type (currently support boxed primitives and String)
* **Referenced holes**: All holes can be referenced by suffix @ and number. This is intended to fill cells with the same data.
For example, consider next template:
```
if (~[EXPR_boolean@1]~) {
    if (~[EXPR_boolean@1]~) {
        ~[VAR_String]~ = ~[EXPR_String]~;
    }
}
```
All placeholders with same references will be filled by same expressions:
```
if (param.isEmpty()) {
    if (param.isEmpty()) {
        ~[VAR_String]~ = ~[EXPR_String]~;
    }
}
```
It works for any types of placeholders (VAR, EXPR, CONST, TYPE)

### Examples:
Let's consider a simple source program:
```
public class Main {
    public static void main(String[] args) throws Exception {
        String a = "123";
        System.out.println("a = " + a);
    }
}
```
And template with simple if
```
~main class start~
~template start~
if (~[EXPR_boolean]~) {
    ~[VAR_String]~ = ~[EXPR_String]~;
}
~template end~
~main class end~
```
Choosing random place to insert template:
```
public class Main {
    public static void main(String[] args) throws Exception {
        String a = "123";
        // INSERT TEMPLATE HERE!!!
        System.out.println("a = " + a);
    }
}
```
Fill template placeholders:
```
if (a.isEmpty()) {
    a = a.toLowerCase()
}
```
Resulting code:
```
public class Main {
    public static void main(String[] args) throws Exception {
        String a = "123";
        if (a.isEmpty()) {
            a = a.toLowerCase()
        }
        System.out.println("a = " + a);
    }
}
```

Let's consider more difficult example:
```
~class org.owasp.benchmark.helpers.ArrayHolder start~
package org.owasp.benchmark.helpers;

public class ArrayHolder {
    private String[] values;

    public ArrayHolder(String value) {
        this(new String[] {value, "A", "B"}); // Динамическая инициализация
    }

    public ArrayHolder(String[] initialValues) {
        this.values = initialValues;
    }

    public String[] getValues() {
        return values;
    }
}
~class org.owasp.benchmark.helpers.ArrayHolder end~


~main class start~
~import org.owasp.benchmark.helpers.ArrayHolder;~
~import java.util.*;~
~template start~
ArrayHolder ah = new ArrayHolder(~[EXPR_String]~);
~[VAR_String]~ = ah.getValues()[~[EXPR_int]~];
~template end~
~template start~
ArrayHolder ah = new ArrayHolder(~[VAR_String]~);
~[VAR_String]~ = ah.getValues()[~[EXPR_int]~];
~template end~
~main class end~
```
* ArrayHolder -- additional class in block *~class ... ~*, which can be used in template
* **\~main class start\~** -- main block, containing necessary imports and two template bodies with different cases of ArrayHolder usage\
\
**More samples can be found at the templates [directory](../templates/)**
