//Analysis results: (SpotBugs, [89, 0])
//(Semgrep, [89])
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
public class BenchmarkTest0003726 extends HttpServlet {

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

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

while (names.hasMoreElements() && flag) {

if (flag && flag) {
         param = param;
         param = "def";
         flag = false;
         param = param;
         param = param;
      } else {
         param = param;
         param = "def";
         flag = false;
         param = param;
         param = param;
      }

String name = (String) names.nextElement();

if (param != null && name.length() > 1) {
                name = name.substring(0, param.length() - 1);

if (serialVersionUID == 4L) {
               break;
            }

}

if (!(param instanceof String)) {

if (serialVersionUID == serialVersionUID) {
               break;
            }

throw new IllegalStateException(("Unexpected value of type Char: " + response).toString());
         }

String[] values = request.getParameterValues(name);
            if (values != null) {
                for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];
                    if (value.equals("BenchmarkTest00037")) {
                        param = name;
                        flag = false;

if (request == null) {

if (!flag) {
         throw new AssertionError("Fail");
      }

if (name == null) {
            param = "?";
         }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

}
                }
            }
        }

        String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

        try {
            java.sql.Connection connection =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlConnection();
            java.sql.PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            java.sql.ResultSet.TYPE_FORWARD_ONLY,
                            java.sql.ResultSet.CONCUR_READ_ONLY,
                            java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT);

if (names != null && names.hasMoreElements()) {
            param = names.nextElement(); // just grab first element
        }

if (sql != null && param.length() > 1) {

if (names == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

param = param.substring(0, param.length() - 1);

if (param != null) {
            sql = sql + sql;
         }

}

statement.setString(1, "foo");
            statement.execute();

if (param != null && param.length() > 1) {
                param = param.substring(0, param.length() - 1);
            }

if (param != null && sql.length() > 1) {
                sql = sql.substring(0, param.length() - 1);
            }

org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }

if (sql == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

if (flag && flag) {
         sql = sql;
         sql = "def";
         flag = false;
         param = sql;

if (param == null) {
         param = "";
      }

param = sql;
      } else {
         param = sql;
         param = "def";
         flag = false;
         sql = sql;
         sql = param;

if (param.indexOf("Windows") != -1) {
            sql = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

}

if (names != null && names.hasMoreElements()) {
            param = names.nextElement(); // just grab first element
        }

}
}
