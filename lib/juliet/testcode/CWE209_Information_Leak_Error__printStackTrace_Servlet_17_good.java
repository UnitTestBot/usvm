/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE209_Information_Leak_Error__printStackTrace_Servlet_17.java
Label Definition File: CWE209_Information_Leak_Error.label.xml
Template File: point-flaw-17.tmpl.java
*/
/*
* @description
* CWE: 209 Information exposure through error message
* Sinks: printStackTrace_Servlet
*    GoodSink: Print a generic error message to response
*    BadSink : Print stack trace to response
* Flow Variant: 17 Control flow: for loops
*
* */

package juliet.testcases;

import juliet.support.*;

import javax.servlet.http.*;

public class CWE209_Information_Leak_Error__printStackTrace_Servlet_17_good extends AbstractTestCaseServlet
{
    public void bad(HttpServletRequest request, HttpServletResponse response) throws Throwable
    { return;}

    /* good1() use the GoodSinkBody in the for statement */
    private void good1(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        for(int k = 0; k < 1; k++)
        {
            try
            {
                throw new UnsupportedOperationException();
            }
            catch (UnsupportedOperationException exceptUnsupportedOperation)
            {
                response.getWriter().println("There was an unsupported operation error"); /* FIX: print a generic error message to response */
            }
        }
    }

    public void good(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        good1(request, response);
    }

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
