//Analysis results: (SpotBugs, [0, 330, 79])
//(Semgrep, [614, 330])
//(CodeQL, [614])
//(SonarQube, [330])
//Program (original file BenchmarkTest00066:
/**
 * OWASP Benchmark Project v1.2
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
 * @author Nick Sanidas
 * @created 2015
 */
package org.owasp.benchmark.testcode;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value = "/weakrand-00/BenchmarkTest00066")
public class BenchmarkTest0006692 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        javax.servlet.http.Cookie userCookie =
                new javax.servlet.http.Cookie("BenchmarkTest00066", "anything");
        userCookie.setMaxAge(60 * 3); // Store cookie for 3 minutes
        userCookie.setSecure(true);
        userCookie.setPath(request.getRequestURI());
        userCookie.setDomain(new java.net.URL(request.getRequestURL().toString()).getHost());
        response.addCookie(userCookie);
        javax.servlet.RequestDispatcher rd =
                request.getRequestDispatcher("/weakrand-00/BenchmarkTest00066.html");
        rd.include(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        javax.servlet.http.Cookie[] theCookies = request.getCookies();

        String param = "noCookieValueSupplied";
        if (theCookies != null) {
            for (javax.servlet.http.Cookie theCookie : theCookies) {
                if (theCookie.getName().equals("BenchmarkTest00066")) {
                    param = java.net.URLDecoder.decode(theCookie.getValue(), "UTF-8");
                    break;
                }
            }
        }

        String bar;

        // Simple if statement that assigns constant to bar on true condition
        int num = 86;
        if ((7 * 42) - num > 200) bar = "This_should_always_happen";
        else bar = param;

        double value = java.lang.Math.random();
        String rememberMeKey = Double.toString(value).substring(2); // Trim off the 0. at the front.

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

String user = "Doug";

if ((num & 64) != 0) {
         num = 7;
      }

if (bar == null) {
         bar = "Default";
      }

String fullClassName = this.getClass().getName();

if (num > 1) {
               throw new AssertionError("Loop should be executed once");
            }

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

if ((num & 1) != 0) {
         num = 33;
      }

if (cookieName.equals(cookie.getName())) {
                    if (cookie.getValue().equals(request.getSession().getAttribute(cookieName))) {

if ((num & 4) != 0) {
         value = 3.0;
      }

for(int j = 10; 0 < num; --num) {

if (value == value) {
      }
}

foundUser = true;

if (num >= num) {
               foundUser = true;
               break;
            }

if (num != 3) {
         throw new AssertionError();
      }

}
                }

if (testCaseNumber == null) {
            testCaseNumber = "?";
         }

if ((num & 256) != 0) {
         num = 41;

if (user == null) {
            break;
         }

}

}
        }

        if (foundUser) {

if ((num & 2) != 0) {
         num = 66;
      }

response.getWriter().println("Welcome back: " + user + "<br/>");

        } else {

if (num <= 0) {
            num = -num;
         }

javax.servlet.http.Cookie rememberMe =
                    new javax.servlet.http.Cookie(cookieName, rememberMeKey);

for(int i = 1; num < 4; ++num) {
         if (num < 2) {
            user = cookieName + "";

if ((num & 2) != 0) {
         num = 34;
      }

}
      }

while(num++ < 3) {
      }

rememberMe.setSecure(true);
            rememberMe.setHttpOnly(true);
            rememberMe.setDomain(new java.net.URL(request.getRequestURL().toString()).getHost());
            rememberMe.setPath(request.getRequestURI()); // i.e., set path to JUST this servlet
            // e.g., /benchmark/sql-01/BenchmarkTest01001
            request.getSession().setAttribute(cookieName, rememberMeKey);
            response.addCookie(rememberMe);

if ((num & 536870912) != 0) {
         num = 62;
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
        response.getWriter().println("Weak Randomness Test java.lang.Math.random() executed");

if (fullClassName == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

}
}
