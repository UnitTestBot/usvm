/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE83_XSS_Attribute__Servlet_URLConnection_21.java
Label Definition File: CWE83_XSS_Attribute__Servlet.label.xml
Template File: sources-sink-21.tmpl.java
*/
/*
 * @description
 * CWE: 83 Cross Site Scripting (XSS) in attributes; Examples(replace QUOTE with an actual double quote): ?img_loc=http://www.google.comQUOTE%20onerror=QUOTEalert(1) and ?img_loc=http://www.google.comQUOTE%20onerror=QUOTEjavascript:alert(1)
 * BadSource: URLConnection Read data from a web server with URLConnection
 * GoodSource: A hardcoded string
 * Sinks: printlnServlet
 *    BadSink : XSS in img src attribute
 * Flow Variant: 21 Control flow: Flow controlled by value of a private variable. All functions contained in one file.
 *
 * */

package juliet.testcases;

import juliet.support.*;

import javax.servlet.http.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import java.util.logging.Level;

public class CWE83_XSS_Attribute__Servlet_URLConnection_21_good extends AbstractTestCaseServlet
{
    /* The variable below is used to drive control flow in the source function */
    private boolean badPrivate = false;

    public void bad(HttpServletRequest request, HttpServletResponse response) throws Throwable
    { return;}

    /* The variables below are used to drive control flow in the source functions. */
    private boolean goodG2B1_private = false;
    private boolean goodG2B2_private = false;

    public void good(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        goodG2B1(request, response);
        goodG2B2(request, response);
    }

    /* goodG2B1() - use goodsource and badsink by setting the variable to false instead of true */
    private void goodG2B1(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;

        goodG2B1_private = false;
        data = goodG2B1_source(request, response);

        if (data != null)
        {
            /* POTENTIAL FLAW: Input is not verified/sanitized before use in an image tag */
            response.getWriter().println("<br>bad() - <img src=\"" + data + "\">");
        }

    }

    private String goodG2B1_source(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data = null;

        if (goodG2B1_private)
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }
        else
        {

            /* FIX: Use a hardcoded string */
            data = "foo";

        }

        return data;
    }

    /* goodG2B2() - use goodsource and badsink by reversing the blocks in the if in the sink function */
    private void goodG2B2(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;

        goodG2B2_private = true;
        data = goodG2B2_source(request, response);

        if (data != null)
        {
            /* POTENTIAL FLAW: Input is not verified/sanitized before use in an image tag */
            response.getWriter().println("<br>bad() - <img src=\"" + data + "\">");
        }

    }

    private String goodG2B2_source(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data = null;

        if (goodG2B2_private)
        {
            /* FIX: Use a hardcoded string */
            data = "foo";
        }
        else
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }

        return data;
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
