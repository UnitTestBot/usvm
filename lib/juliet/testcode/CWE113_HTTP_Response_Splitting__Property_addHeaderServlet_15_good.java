/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE113_HTTP_Response_Splitting__Property_addHeaderServlet_15.java
Label Definition File: CWE113_HTTP_Response_Splitting.label.xml
Template File: sources-sinks-15.tmpl.java
*/
/*
* @description
* CWE: 113 HTTP Response Splitting
* BadSource: Property Read data from a system property
* GoodSource: A hardcoded string
* Sinks: addHeaderServlet
*    GoodSink: URLEncode input
*    BadSink : querystring to addHeader()
* Flow Variant: 15 Control flow: switch(6) and switch(7)
*
* */

package juliet.testcases;
import juliet.support.*;

import javax.servlet.http.*;

import java.net.URLEncoder;

public class CWE113_HTTP_Response_Splitting__Property_addHeaderServlet_15_good extends AbstractTestCaseServlet
{
    public void bad(HttpServletRequest request, HttpServletResponse response) throws Throwable
    { return;}

    /* goodG2B1() - use goodsource and badsink by changing the first switch to switch(5) */
    private void goodG2B1(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;

        switch (5)
        {
        case 6:
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
            break;
        default:
            /* FIX: Use a hardcoded string */
            data = "foo";
            break;
        }

        switch (7)
        {
        case 7:
            /* POTENTIAL FLAW: Input from file not verified */
            if (data != null)
            {
                response.addHeader("Location", "/author.jsp?lang=" + data);
            }
            break;
        default:
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run */
            IO.writeLine("Benign, fixed string");
            break;
        }
    }

    /* goodG2B2() - use goodsource and badsink by reversing the blocks in the first switch  */
    private void goodG2B2(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;

        switch (6)
        {
        case 6:
            /* FIX: Use a hardcoded string */
            data = "foo";
            break;
        default:
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
            break;
        }

        switch (7)
        {
        case 7:
            /* POTENTIAL FLAW: Input from file not verified */
            if (data != null)
            {
                response.addHeader("Location", "/author.jsp?lang=" + data);
            }
            break;
        default:
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run */
            IO.writeLine("Benign, fixed string");
            break;
        }
    }

    /* goodB2G1() - use badsource and goodsink by changing the second switch to switch(8) */
    private void goodB2G1(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;

        switch (6)
        {
        case 6:
            /* get system property user.home */
            /* POTENTIAL FLAW: Read data from a system property */
            data = System.getProperty("user.home");
            break;
        default:
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
            break;
        }

        switch (8)
        {
        case 7:
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run */
            IO.writeLine("Benign, fixed string");
            break;
        default:
            /* FIX: use URLEncoder.encode to hex-encode non-alphanumerics */
            if (data != null)
            {
                data = URLEncoder.encode(data, "UTF-8");
                response.addHeader("Location", "/author.jsp?lang=" + data);
            }
            break;
        }
    }

    /* goodB2G2() - use badsource and goodsink by reversing the blocks in the second switch  */
    private void goodB2G2(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;

        switch (6)
        {
        case 6:
            /* get system property user.home */
            /* POTENTIAL FLAW: Read data from a system property */
            data = System.getProperty("user.home");
            break;
        default:
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
            break;
        }

        switch (7)
        {
        case 7:
            /* FIX: use URLEncoder.encode to hex-encode non-alphanumerics */
            if (data != null)
            {
                data = URLEncoder.encode(data, "UTF-8");
                response.addHeader("Location", "/author.jsp?lang=" + data);
            }
            break;
        default:
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run */
            IO.writeLine("Benign, fixed string");
            break;
        }
    }

    public void good(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        goodG2B1(request, response);
        goodG2B2(request, response);
        goodB2G1(request, response);
        goodB2G2(request, response);
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
