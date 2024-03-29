//Analysis results: (SpotBugs, [0])
//(Semgrep, [])
//(CodeQL, [])
//(SonarQube, [89])
//Program (original file BenchmarkTest00034:
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

@WebServlet(value = "/sqli-00/BenchmarkTest00034")
public class BenchmarkTest0003493 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

if (((Number)request).intValue() != 2) {
               throw new AssertionError("Fail");
            }

doPost(request, response);

if (!(response instanceof Number)) {
                     throw new IllegalStateException();
                  }

}

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // some code
        response.setContentType("text/html;charset=UTF-8");

        java.util.Map<String, String[]> map = request.getParameterMap();
        String param = "";

if (param != null && param.length() > 1) {

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

if (param != null) param = param.split(" ")[0];

param = param.substring(0, param.length() - 1);
        }

if (!map.isEmpty()) {

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

String[] values = map.get("BenchmarkTest00034");

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";

if (param == null) {

if (param == null) {
         param = "OK";
      }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

}

if (param != null) {
            param = param + param;

if (((Object[])values)[0] == null) {
                                 throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<*>");
                              }

}

if (values != null) param = values[0];

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";

if (values == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

switch (param) {
               case "abc":
               case "cde":

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

case "efg":

if (param == null) {
            param = "?";
         }

case "ghi":
            }

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

}

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

switch (param) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

String sql = "SELECT * from USERS where USERNAME='foo' and PASSWORD='" + param + "'";

        try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            statement.execute(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
