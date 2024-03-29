//Analysis results: (SpotBugs, [330, 79])
//(Semgrep, [330])
//(CodeQL, [614])
//(SonarQube, [330])
//Program (original file BenchmarkTest00023:
/**
 * OWASP Benchmark v1.2
 *
 * <p>This file is part of the Open Web Application Security Project (OWASP) Benchmark Project. For
 * details, please see <a
 * href="https://owasp.org/www-project-benchmark/">https://owasp.org/www-project-benchmark/</a>.
 *
 * <p>The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, version 2.
 *
 * <p>The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * @author Dave Wichers
 * @created 2015
 */
package org.owasp.benchmark.testcode;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value = "/weakrand-00/BenchmarkTest00023")
public class BenchmarkTest0002310 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // some code
        response.setContentType("text/html;charset=UTF-8");

        String param = request.getParameter("BenchmarkTest00023");
        if (param == null) param = "";

        float rand = new java.util.Random().nextFloat();
        String rememberMeKey = Float.toString(rand).substring(2); // Trim off the 0. at the front.

        String user = "Floyd";

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

String fullClassName = this.getClass().getName();
        String testCaseNumber =
                fullClassName.substring(
                        fullClassName.lastIndexOf('.') + 1 + "BenchmarkTest".length());
        user += testCaseNumber;

        String cookieName = "rememberMe" + testCaseNumber;

        boolean foundUser = false;
        javax.servlet.http.Cookie[] cookies = request.getCookies();

if (rememberMeKey == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

if (!foundUser) {

if (foundUser) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

throw new AssertionError("Fail");
      }

if (cookies != null) {

if (user == null) {
         cookieName = "";
      }

if (cookieName == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

if (cookieName != null) {

if (!foundUser) {
         throw new AssertionError("Fail");
      }

cookieName = user + rememberMeKey;
         }

for (int i = 0; !foundUser && i < cookies.length; i++) {
                javax.servlet.http.Cookie cookie = cookies[i];
                if (cookieName.equals(cookie.getName())) {
                    if (cookie.getValue().equals(request.getSession().getAttribute(cookieName))) {

if (rand > 0.0F) {
         ++rand;
      }

foundUser = true;
                    }

if (serialVersionUID == serialVersionUID) {
                  break;
               }

}
            }

if (cookieName == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

}

        if (foundUser) {

if (fullClassName == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

response.getWriter().println("Welcome back: " + user + "<br/>");

if (param != null && cookieName.length() > 1) {
            param = fullClassName.substring(0, testCaseNumber.length() - 1);
        }

} else {
            javax.servlet.http.Cookie rememberMe =
                    new javax.servlet.http.Cookie(cookieName, rememberMeKey);

if (rememberMeKey != null) {
            fullClassName = rememberMeKey + testCaseNumber;

if (rand == rand) {
      }

}

rememberMe.setSecure(true);
            rememberMe.setHttpOnly(true);
            rememberMe.setDomain(new java.net.URL(request.getRequestURL().toString()).getHost());
            rememberMe.setPath(request.getRequestURI()); // i.e., set path to JUST this servlet
            // e.g., /benchmark/sql-01/BenchmarkTest01001

if (fullClassName == null) {

if (fullClassName != null) cookieName = user.split(" ")[0];

if (rememberMeKey != null && testCaseNumber.length() > 1) {
                cookieName = testCaseNumber.substring(0, cookieName.length() - 1);
            }

user = "";
      }

request.getSession().setAttribute(cookieName, rememberMeKey);
            response.addCookie(rememberMe);

if (!(user instanceof String)) {
            throw new IllegalStateException(("Unexpected value of type Char: " + param).toString());
         }

response.getWriter()
                    .println(
                            user
                                    + " has been remembered with cookie: "
                                    + rememberMe.getName()
                                    + " whose value is: "
                                    + rememberMe.getValue()
                                    + "<br/>");
        }

        response.getWriter().println("Weak Randomness Test java.util.Random.nextFloat() executed");

if (rememberMeKey == null) {
         cookieName = "OK";
      }

}
}
