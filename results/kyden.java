//Analysis results: (SpotBugs, [0])
//(Semgrep, [])
//(CodeQL, [])
//(SonarQube, [89])
//Program (original file BenchmarkTest00037:
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

@WebServlet(value = "/sqli-00/BenchmarkTest00037")
public class BenchmarkTest0003794 extends HttpServlet {

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

        String param = "";
        boolean flag = true;
        java.util.Enumeration<String> names = request.getParameterNames();

if (request == null) {

if (((Number)names).intValue() != 2) {

if (param == null) {
         param = "OK";
      }

throw new AssertionError("Fail");
            }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

if (!(names instanceof Number)) {
                     throw new IllegalStateException();
                  }

while (names.hasMoreElements() && flag) {

if (!(names instanceof Number)) {
                     throw new IllegalStateException();
                  }

String name = (String) names.nextElement();

if (name == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

String[] values = request.getParameterValues(name);
            if (values != null) {

if (param instanceof String) {
            ((String)name).charAt(0);
         }

for (int i = 0; i < values.length && flag; i++) {

if (param == null) {

if (!flag) {

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

throw new AssertionError("Fail");
      }

name = "?";
         }

String value = values[i];

switch (param) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

if (flag) {

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

throw new IllegalArgumentException("Array contains more than one matching element.");
            }

if (value.equals("BenchmarkTest00037")) {
                        param = name;
                        flag = false;

if (names != null && names.hasMoreElements()) {
            param = names.nextElement(); // just grab first element

if (!(request instanceof Number)) {
                     throw new IllegalStateException();
                  }

}

}
                }
            }
        }

        String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

        try {
            java.sql.Connection connection =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlConnection();

if (!flag) {

if (sql == null) sql = "";

throw new AssertionError("Fail");
      }

if (sql.indexOf("Windows") != -1) {
            param = "cmd.exe";
            sql = "/c";
        } else {
            param = "sh";

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

sql = "-c";
        }

java.sql.PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            java.sql.ResultSet.TYPE_FORWARD_ONLY,
                            java.sql.ResultSet.CONCUR_READ_ONLY,
                            java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT);
            statement.setString(1, "foo");
            statement.execute();
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
