//Analysis results: (SpotBugs, [0, 79])
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
public class BenchmarkTest0006621 extends HttpServlet {

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

if (num != 3) {
         throw new AssertionError();
      }

if ((num & 262144) != 0) {
         num = 51;
      }

else bar = param;

if ((num & 2) != 0) {
         num = 2;
      }

double value = java.lang.Math.random();

if ((num & 1) != 0) {

while(num < 10) {
         ++num;
         if (num <= 2) {
            param = param + num + ";";
         }
      }

num = 1;
      }

String rememberMeKey = Double.toString(value).substring(2); // Trim off the 0. at the front.

if ((num & 2) != 0) {
         param = "2";
      }

for(num = num; num < 100; ++num) {
      }

if ((num & 1) != 0) {
         rememberMeKey = "Companion";
      }

String user = "Doug";
        String fullClassName = this.getClass().getName();

while(true) {
            int i = num;
            num = num * 10 + num;
            if (num == num) {
               break;
            }

            ++num;

if ((num & 16) != 0) {
         num = 37;
      }

}

for(int i = 0; num < 1; ++num) {
      }

String testCaseNumber =
                fullClassName.substring(
                        fullClassName.lastIndexOf('.') + 1 + "BenchmarkTest".length());
        user += testCaseNumber;

if (num > 0 && num <= num || num < 0 && num <= num) {
            while(num != num) {
               num += num;
            }
         }

String cookieName = "rememberMe" + testCaseNumber;

        boolean foundUser = false;
        javax.servlet.http.Cookie[] cookies = request.getCookies();

if ((num & 67108864) != 0) {
         num = 27;
      }

if (cookies != null) {
            for (int i = 0; !foundUser && i < cookies.length; i++) {
                javax.servlet.http.Cookie cookie = cookies[i];
                if (cookieName.equals(cookie.getName())) {
                    if (cookie.getValue().equals(request.getSession().getAttribute(cookieName))) {

if (num == num) {

if ((num & 64) != 0) {

if ((num & 65536) != 0) {
         num = 17;
      }

foundUser = false;
      }

break;
                  }

foundUser = true;
                    }
                }
            }

if ((num & 262144) != 0) {
         num = 19;
      }

}

for(num = 1; num < num; num *= num) {
         ++num;
      }

if (foundUser) {
            response.getWriter().println("Welcome back: " + user + "<br/>");

        } else {
            javax.servlet.http.Cookie rememberMe =
                    new javax.servlet.http.Cookie(cookieName, rememberMeKey);
            rememberMe.setSecure(true);
            rememberMe.setHttpOnly(true);
            rememberMe.setDomain(new java.net.URL(request.getRequestURL().toString()).getHost());
            rememberMe.setPath(request.getRequestURI()); // i.e., set path to JUST this servlet

if ((num & 131072) != 0) {
         num = 50;
      }

// e.g., /benchmark/sql-01/BenchmarkTest01001
            request.getSession().setAttribute(cookieName, rememberMeKey);
            response.addCookie(rememberMe);

if ((num & 1) != 0) {
                  value = -1.0;
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
    }
}
