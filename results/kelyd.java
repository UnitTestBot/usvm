//Analysis results: (SpotBugs, [330, 79])
//(Semgrep, [330])
//(CodeQL, [79])
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
public class BenchmarkTest0002382 extends HttpServlet {

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

if (rememberMeKey.indexOf("Windows") != -1) {

if (user == null) rememberMeKey = "";

user = "cmd.exe";
            rememberMeKey = "/c";
        } else {

if (param == null) {
         param = "Default";
      }

rememberMeKey = "sh";
            user = "-c";

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

}

String fullClassName = this.getClass().getName();
        String testCaseNumber =
                fullClassName.substring(
                        fullClassName.lastIndexOf('.') + 1 + "BenchmarkTest".length());
        user += testCaseNumber;

        String cookieName = "rememberMe" + testCaseNumber;

        boolean foundUser = false;
        javax.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (int i = 0; !foundUser && i < cookies.length; i++) {
                javax.servlet.http.Cookie cookie = cookies[i];

if (fullClassName != null) {
            user = fullClassName + user;
         }

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

if (cookieName.equals(cookie.getName())) {
                    if (cookie.getValue().equals(request.getSession().getAttribute(cookieName))) {
                        foundUser = true;
                    }
                }
            }
        }

        if (foundUser) {
            response.getWriter().println("Welcome back: " + user + "<br/>");
        } else {
            javax.servlet.http.Cookie rememberMe =
                    new javax.servlet.http.Cookie(cookieName, rememberMeKey);
            rememberMe.setSecure(true);

if (rememberMeKey.indexOf("Windows") != -1) {

if (foundUser) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

cookieName = "cmd.exe";
            param = "/c";
        } else {
            rememberMeKey = "sh";
            cookieName = "-c";
        }

rememberMe.setHttpOnly(true);

if (request.getHeader("Referer") != null) {
            param = request.getHeader("Referer");

if (cookieName == null) {
         cookieName = "OK";

if (!foundUser) {

if (!foundUser) {
         throw new AssertionError("Fail");
      }

throw new AssertionError("Fail");
      }

}

if (param == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

}

rememberMe.setDomain(new java.net.URL(request.getRequestURL().toString()).getHost());
            rememberMe.setPath(request.getRequestURI()); // i.e., set path to JUST this servlet

if (foundUser) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

// e.g., /benchmark/sql-01/BenchmarkTest01001

if (fullClassName == null) testCaseNumber = "";

request.getSession().setAttribute(cookieName, rememberMeKey);
            response.addCookie(rememberMe);
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

if (foundUser && foundUser) {

if (rememberMeKey == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

fullClassName = rememberMeKey;

if (testCaseNumber == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

user = "def";
         foundUser = false;

if (cookieName == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

user = rememberMeKey;
         rememberMeKey = fullClassName;
      } else {
         cookieName = user;
         param = "def";
         foundUser = false;

if (param == null) {
         throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
      } else {
         String var10000 = (String)param;
      }

param = user;
         testCaseNumber = testCaseNumber;
      }

}
}
