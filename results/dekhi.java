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
public class BenchmarkTest0003434 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

}

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // some code
        response.setContentType("text/html;charset=UTF-8");

        java.util.Map<String, String[]> map = request.getParameterMap();

if (((Number)request).intValue() != 2) {
               throw new AssertionError("Fail");
            }

String param = "";

if (param == null) {

if (!(map instanceof Number)) {
                     throw new IllegalStateException();
                  }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

if (!map.isEmpty()) {
            String[] values = map.get("BenchmarkTest00034");
            if (values != null) param = values[0];
        }

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

if (param == null) {
         param = "OK";
      }

if (!(map instanceof Number)) {
                     throw new IllegalStateException();
                  }

String sql = "SELECT * from USERS where USERNAME='foo' and PASSWORD='" + param + "'";

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

sql = "/c";
        } else {
            param = "sh";
            sql = "-c";
        }

if (param == null) {
         param = "OK";

if (param != null && param.length() > 1) {

if (sql != null) {
            sql = param + param;
         }

sql = param.substring(0, param.length() - 1);
        }

}

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

if (sql != null && sql.length() > 1) {

if (sql == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

if (param == null) {
         param = "";
      }

sql = param.substring(0, param.length() - 1);
            }

try {

switch (param) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            statement.execute(sql, java.sql.Statement.RETURN_GENERATED_KEYS);

if (param == null) {
            param = "?";
         }

org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {

if (param == null) param = "";

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
