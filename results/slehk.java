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
public class BenchmarkTest0003414 extends HttpServlet {

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

if (response == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

response.setContentType("text/html;charset=UTF-8");

        java.util.Map<String, String[]> map = request.getParameterMap();
        String param = "";

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

if (!map.isEmpty()) {

if (param != null) param = param.split(" ")[0];

if (param != null && param.length() > 1) {
            param = param.substring(0, param.length() - 1);
        }

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

String[] values = map.get("BenchmarkTest00034");

if (request == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

if (request.getHeader("Referer") != null) {
            param = request.getHeader("Referer");
        }

if (values != null) param = values[0];

if (!(request instanceof Number)) {
                     throw new IllegalStateException();
                  }

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

}

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

String sql = "SELECT * from USERS where USERNAME='foo' and PASSWORD='" + param + "'";

if (sql == null) {
         sql = "Default";
      }

if (param != null && sql.length() > 1) {

if (param != null) {
            sql = sql + sql;
         }

param = param.substring(0, sql.length() - 1);
        }

try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();

if (!(map instanceof Number)) {

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

throw new IllegalStateException();
                  }

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

statement.execute(sql, java.sql.Statement.RETURN_GENERATED_KEYS);

if (sql == null) {
         sql = "Default";
      }

org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }

if (serialVersionUID <= 0L) {

if (sql == null) {
            sql = "?";
         }

throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

switch (sql) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

}
}
