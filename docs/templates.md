## General Documentation for Code Templates
### Introduction

Code templates are predefined structures that help automate the process of SAST-fuzzing. 
This documentation provides general principles and rules for working with code templates.
Code templates inserts in the random place of source test sample and fills with variables/generated expressions available in scope.
The main feature of the templates is the presence of **typed placeholders**.

### Structure of Templates
Typical directory structure of templates looks like:
```
templates
├── constructors
│   ├── helpers
│   │   ├── A.tmt
│   │   └── B.tmt
│   ├── template1.tmt
│   └── template2.tmt
├── extensions
│   ├── extensions1.tmt
│   └── extensions2.tmt
└──pathSensitivity
    ├── helpers
    │   ├── A.tmt
    │   └── C.tmt
    ├── template1.tmt
    └── template2.tmt

```
* Directory template contains directories for each target language feature
* Extensions - special directory, it contains extensions and macroses
* Any directory with template may contain helpers directory (with auxiliary classes)
#### Helpers
As mentioned before, helpers directory contain auxiliary classes. Helpers can be used via imports in template bodies. Syntax:
```
~class [CLASS_NAME] start~
[CLASS_BODY]
~class [CLASS_NAME] end~
```
**It should not contain any typed placeholder!**

#### Extensions
Extensions directory contains GLOBAL extensions, which can be used by any template via any of special import directives:
* ~extensions import all~ - for importing all extensions
* ~extensions import extensions/[FILE_NAME]~ - for importing extensions extensions from file [FILE_NAME]\
**Examples of extensions:**
```
#Extensions:
~[EXPR_Boolean]~ -> ~[VAR@1_String]~.isEmpty() && ~[VAR@1_String]~.contains(~[EXPR_String]~)
~[EXPR_Boolean]~ -> ~[VAR@1_String]~.isEmpty()
# For probabilistic replacement of the corresponding hole in the template body


#Marcos:
~[MY_EXPR]~ -> ~[VAR_String]~ + ~[EXPR_String]~
# For mandatory replacement of the corresponding hole in the template body
```

Each template consists of the following main components:
1) Local extensions, extensions imports and macros
```   
~extensions start~
~extensions import extensions/extensions~

#Extensions:
~[EXPR_String]~ -> "ABCDEF"
~[EXPR_String]~ -> ~[VAR_String]~ + ~[EXPR_String]~
~[EXPR_String]~ -> ~[VAR@1_String]~ + ~[VAR@1_String]~
~[EXPR_String]~ -> ~[MY_EXPR]~ + "123"

#Macros:
~[MY_EXPR]~ -> Arrays.asList("123", ~[EXPR_String]~, ~[EXPR_String]~).get(2)

~extensions end~
```
2) Main template body
```
~main body start~
TEMPLATE BODY
~main body end~
```
Which contains:
* List of Additional Functions: Contains any helper functions or methods that are required by the main template logic.
    **It also should not contain any typed placeholder!**
```
~helper functions start~
~function [FUNC_NAME] start~
[FUNC_BODY]
~function [FUNC_NAME] end~
~helper functions end~
```
* List of Imports: Specifies the helpers, libraries and packages that need to be imported for the template to work correctly.
```
~helper import [PATH_TO_HELPER]~
~import java.util.*;~
```
* Templates bodies (min 1):
```
~template [TEMPLATE_NAME] start~
TEMPLATE BODY
~template end~
```
Each of which consists of:
* List of auxiliary functions import
```
~helper function [FUNC_NAME]~
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
* **\~[BODY]\~**: Random sequence of lines existing in the source file is inserted in place of the hole
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
~main body start~
~template example_template start~
if (~[EXPR_boolean]~) {
    ~[VAR_String]~ = ~[EXPR_String]~;
}
~template end~
~main body end~
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
\
**More samples can be found at the templates [directory](../templates/)**
