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
public class BenchmarkTest0003773 extends HttpServlet {

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

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

java.util.Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements() && flag) {

if (serialVersionUID == serialVersionUID) {

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {

if (names == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

}

break;
               }

String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);
            if (values != null) {

if (param == null) {
         name = "";
      }

for (int i = 0; i < values.length && flag; i++) {

if (names == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

if (param.indexOf("Windows") != -1) {

if (values == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

if (serialVersionUID == serialVersionUID) {

if (!flag) {
            flag = true;
            break;
         }

break;
                  }

name = "cmd.exe";
            param = "/c";
        } else {

if (names == null) {
         throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
      } else {
         String var10000 = (String)param;
      }

name = "sh";
            param = "-c";
        }

if (serialVersionUID == 8L) {
               break;
            }

String value = values[i];

if (serialVersionUID == 8L) {

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

break;
            }

if (value.equals("BenchmarkTest00037")) {
                        param = name;
                        flag = false;
                    }
                }

if (name != null) name = param.split(" ")[0];

}
        }

        String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

        try {

if (param == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

java.sql.Connection connection =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlConnection();
            java.sql.PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            java.sql.ResultSet.TYPE_FORWARD_ONLY,
                            java.sql.ResultSet.CONCUR_READ_ONLY,
                            java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT);

if (sql != null) sql = param.split(" ")[0];

statement.setString(1, "foo");

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

if (param != null && sql.length() > 1) {
                param = sql.substring(0, sql.length() - 1);
            }

statement.execute();
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }

if (sql == null) {
         sql = "";
      }

}
}
