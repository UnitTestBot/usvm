/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE470_Unsafe_Reflection__getParameter_Servlet_07.java
Label Definition File: CWE470_Unsafe_Reflection.label.xml
Template File: sources-sink-07.tmpl.java
*/
/*
* @description
* CWE: 470 Use of Externally-Controlled Input to Select Classes or Code ('Unsafe Reflection')
* BadSource: getParameter_Servlet Read data from a querystring using getParameter()
* GoodSource: Set data to a hardcoded class name
* BadSink:  Instantiate class named in data
* Flow Variant: 07 Control flow: if(privateFive==5) and if(privateFive!=5)
*
* */

package juliet.testcases;

import juliet.support.*;

import javax.servlet.http.*;


public class CWE470_Unsafe_Reflection__getParameter_Servlet_07_bad extends AbstractTestCaseServlet
{
    /* The variable below is not declared "final", but is never assigned
     * any other value so a tool should be able to identify that reads of
     * this will always give its initialized value.
     */
    private int privateFive = 5;

    /* uses badsource and badsink */
    public void bad(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;
        if (privateFive == 5)
        {
            /* POTENTIAL FLAW: Read data from a querystring using getParameter */
            data = request.getParameter("name");
        }
        else
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }

        /* POTENTIAL FLAW: Instantiate object of class named in data (which may be from external input) */
        Class<?> tempClass = Class.forName(data);
        Object tempClassObject = tempClass.newInstance();

        IO.writeLine(tempClassObject.toString()); /* Use tempClassObject in some way */

    }

    public void good(HttpServletRequest request, HttpServletResponse response) throws Throwable
    { return;}

    /* Below is the main(). It is only used when building this testcase on
     * its own for testing or for building a binary to use in testing binary
     * analysis tools. It is not used when compiling all the juliet.testcases as one
     * application, which is how source code analysis tools are tested.
     */
    public static void main(String[] args) throws ClassNotFoundException,
           InstantiationException, IllegalAccessException
    {
        mainFromParent(args);
    }
}
