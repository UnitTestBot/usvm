/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE113_HTTP_Response_Splitting__getQueryString_Servlet_addCookieServlet_07.java
Label Definition File: CWE113_HTTP_Response_Splitting.label.xml
Template File: sources-sinks-07.tmpl.java
*/
/*
* @description
* CWE: 113 HTTP Response Splitting
* BadSource: getQueryString_Servlet Parse id param out of the URL query string (without using getParameter())
* GoodSource: A hardcoded string
* Sinks: addCookieServlet
*    GoodSink: URLEncode input
*    BadSink : querystring to addCookie()
* Flow Variant: 07 Control flow: if(privateFive==5) and if(privateFive!=5)
*
* */

package juliet.testcases;
import juliet.support.*;

import javax.servlet.http.*;

import java.util.StringTokenizer;

import java.net.URLEncoder;

public class CWE113_HTTP_Response_Splitting__getQueryString_Servlet_addCookieServlet_07_good extends AbstractTestCaseServlet
{
    /* The variable below is not declared "final", but is never assigned
     * any other value so a tool should be able to identify that reads of
     * this will always give its initialized value. */
    private int privateFive = 5;

    public void bad(HttpServletRequest request, HttpServletResponse response) throws Throwable
    { return;}

    /* goodG2B1() - use goodsource and badsink by changing first privateFive==5 to privateFive!=5 */
    private void goodG2B1(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;
        if (privateFive!=5)
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

        if (privateFive==5)
        {
            if (data != null)
            {
                Cookie cookieSink = new Cookie("lang", data);
                /* POTENTIAL FLAW: Input not verified before inclusion in the cookie */
                response.addCookie(cookieSink);
            }
        }
    }

    /* goodG2B2() - use goodsource and badsink by reversing statements in first if */
    private void goodG2B2(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;
        if (privateFive==5)
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

        if (privateFive==5)
        {
            if (data != null)
            {
                Cookie cookieSink = new Cookie("lang", data);
                /* POTENTIAL FLAW: Input not verified before inclusion in the cookie */
                response.addCookie(cookieSink);
            }
        }
    }

    /* goodB2G1() - use badsource and goodsink by changing second privateFive==5 to privateFive!=5 */
    private void goodB2G1(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;
        if (privateFive==5)
        {
            data = ""; /* initialize data in case id is not in query string */
            /* POTENTIAL FLAW: Parse id param out of the URL querystring (without using getParameter()) */
            {
                StringTokenizer tokenizer = new StringTokenizer(request.getQueryString(), "&");
                while (tokenizer.hasMoreTokens())
                {
                    String token = tokenizer.nextToken(); /* a token will be like "id=foo" */
                    if(token.startsWith("id=")) /* check if we have the "id" parameter" */
                    {
                        data = token.substring(3); /* set data to "foo" */
                        break; /* exit while loop */
                    }
                }
            }
        }
        else
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }

        if (privateFive!=5)
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run */
            IO.writeLine("Benign, fixed string");
        }
        else
        {

            if (data != null)
            {
                Cookie cookieSink = new Cookie("lang", URLEncoder.encode(data, "UTF-8"));
                /* FIX: use URLEncoder.encode to hex-encode non-alphanumerics */
                response.addCookie(cookieSink);
            }

        }
    }

    /* goodB2G2() - use badsource and goodsink by reversing statements in second if  */
    private void goodB2G2(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;
        if (privateFive==5)
        {
            data = ""; /* initialize data in case id is not in query string */
            /* POTENTIAL FLAW: Parse id param out of the URL querystring (without using getParameter()) */
            {
                StringTokenizer tokenizer = new StringTokenizer(request.getQueryString(), "&");
                while (tokenizer.hasMoreTokens())
                {
                    String token = tokenizer.nextToken(); /* a token will be like "id=foo" */
                    if(token.startsWith("id=")) /* check if we have the "id" parameter" */
                    {
                        data = token.substring(3); /* set data to "foo" */
                        break; /* exit while loop */
                    }
                }
            }
        }
        else
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }

        if (privateFive==5)
        {
            if (data != null)
            {
                Cookie cookieSink = new Cookie("lang", URLEncoder.encode(data, "UTF-8"));
                /* FIX: use URLEncoder.encode to hex-encode non-alphanumerics */
                response.addCookie(cookieSink);
            }
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
