//Analysis results: (SpotBugs, [0])
//(Semgrep, [89])
//(CodeQL, [89])
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
public class BenchmarkTest0003751 extends HttpServlet {

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

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

response.setContentType("text/html;charset=UTF-8");

        String param = "";
        boolean flag = true;
        java.util.Enumeration<String> names = request.getParameterNames();

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

if (!(param instanceof String)) {

if (param != null && param.length() > 1) {
                param = param.substring(0, param.length() - 1);
            }

throw new IllegalStateException(("Unexpected value of type Char: " + names).toString());
         }

while (names.hasMoreElements() && flag) {
            String name = (String) names.nextElement();

if (name != null) name = name.split(" ")[0];

if (name == null) {
            name = "?";
         }

if (serialVersionUID == serialVersionUID) {

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

break;
            }

String[] values = request.getParameterValues(name);
            if (values != null) {
                for (int i = 0; i < values.length && flag; i++) {

if (flag) {

if (values != null && values.length > 0) param = values[0];
        else name = "";

if (param != null && param.length() > 1) {
            param = param.substring(0, name.length() - 1);
        }

throw new IllegalArgumentException("Array contains more than one matching element.");
            }

String value = values[i];
if (values != null) name = values[0];

if (value.equals("BenchmarkTest00037")) {

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

param = name;

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

if (param.indexOf("Windows") != -1) {
            value = "cmd.exe";
            value = "/c";
        } else {
            param = "sh";
            name = "-c";
        }

if (!flag) {
            flag = true;
            break;
         }

if (param != null) {
            param = param + param;
         }

flag = false;
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
            statement.setString(1, "foo");

if (param == null) {
            sql = "?";
         }

if (param != null && param.length() > 1) {
                param = sql.substring(0, sql.length() - 1);
            }

statement.execute();
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

} catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
