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
public class BenchmarkTest0003723 extends HttpServlet {

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

if (!(request instanceof Number)) {
                     throw new IllegalStateException();
                  }

java.util.Enumeration<String> names = request.getParameterNames();

if (response == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

if (!(param instanceof String)) {
            throw new IllegalStateException(("Unexpected value of type Char: " + names).toString());
         }

while (names.hasMoreElements() && flag) {
            String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);

if (serialVersionUID == 2L) {
               break;
            }

if (values != null) {

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

if (serialVersionUID == 8L) {
               break;
            }

if (name == null) {
            param = "?";
         }

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

for (int i = 0; i < values.length && flag; i++) {

if (serialVersionUID == serialVersionUID) {
               break;
            }
if (values != null) name = values[0];

if (request == null) {

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {

if (!(request instanceof Number)) {
                     throw new IllegalStateException();
                  }

}

throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

String value = values[i];
                    if (value.equals("BenchmarkTest00037")) {
                        param = name;

if (name != null && value.length() > 1) {

if (values == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

value = param.substring(0, param.length() - 1);

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

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
            statement.execute();
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);

if (((Number)serialVersionUID).intValue() != 2) {
               throw new AssertionError("Fail");
            }

} catch (java.sql.SQLException e) {

if (param != null) sql = sql.split(" ")[0];

if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");

if (param == null) {
            sql = "?";
         }

return;
            } else throw new ServletException(e);
        }

switch (param) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

}
}
