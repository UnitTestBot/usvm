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
public class BenchmarkTest000344 extends HttpServlet {

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

        java.util.Map<String, String[]> map = request.getParameterMap();
        String param = "";

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

if (((Number)map).intValue() != 2) {

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

throw new AssertionError("Fail");
            }

if (param != null && param.length() > 1) {
                param = param.substring(0, param.length() - 1);
            }

param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

if (param != null && param.length() > 1) {
                param = param.substring(0, param.length() - 1);
            }

if (!map.isEmpty()) {
            String[] values = map.get("BenchmarkTest00034");
            if (values != null) param = values[0];
        }

        String sql = "SELECT * from USERS where USERNAME='foo' and PASSWORD='" + param + "'";

        try {

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            sql = "/c";
        } else {
            sql = "sh";
            sql = "-c";
        }

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {

if (sql == null) {
         sql = "Default";
      }

if ("".equals(sql)) sql = "No cookie value supplied";

if (!(request instanceof Number)) {
                     throw new IllegalStateException();
                  }

if (sql == null) {
         param = "Default";
      }

if (response == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

switch (sql) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

if (sql != null) param = sql.split(" ")[0];

if (map == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

}

java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();

if (sql == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

statement.execute(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);

if (sql == null) {
            param = "?";
         }

} catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");

if (sql != null) sql = sql.split(" ")[0];

return;
            } else throw new ServletException(e);
        }

if (request.getHeader("Referer") != null) {
            param = request.getHeader("Referer");
        }

}
}
