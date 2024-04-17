//Analysis results:
//Tool name: SpotBugs Results: [89, 0]
//Tool name: CodeQL Results: [89]
//Tool name: SonarQube Results: [89]
//Tool name: Usvm Results: []
//Tool name: Semgrep Results: [89]
//Original file name: BenchmarkTest01314
//Original file CWE's: [89]  
//Mutation info: Insert template from templates/cycles/recursion.tmt with index 4 
//Program:
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
import java.util.*;

@WebServlet(value = "/sqli-02/BenchmarkTest01314")
public class BenchmarkTest01314836 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        String param = request.getParameter("BenchmarkTest01314");
        if (param == null) param = "";

if (countVowels(param) != 0) {
    param = response.getHeader(param);
}
String bar = new Test().doSomething(request, param);

        String sql = "INSERT INTO users (username, password) VALUES ('foo','" + bar + "')";

        try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            int count = statement.executeUpdate(sql);
            org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    } // end doPost

public int countVowels(String s) {
        if (s.isEmpty()) {
            return 0;
        } else {
            char c = s.charAt(0);
            if (isVowel(c)) {
                return 1 + countConsonants(s.substring(1));
            } else {
                return countVowels(s.substring(1));
            }
        }
    }

public int countConsonants(String s) {
        if (s.isEmpty()) {
            return 0;
        } else {
            char c = s.charAt(0);
            if (isVowel(c)) {
                return countConsonants(s.substring(1));
            } else {
                return 1 + countVowels(s.substring(1));
            }
        }
    }

public boolean isVowel(char c) {
        return "aeiou".contains(Character.toString(c).toLowerCase());
    }



    private class Test {

        public String doSomething(HttpServletRequest request, String param)
                throws ServletException, IOException {

            String bar;

            // Simple if statement that assigns param to bar on true condition
            int num = 196;
            if ((500 / 42) + num > 200) bar = param;
            else bar = "This should never happen";

            return bar;
        }
    } // end innerclass Test
} // end DataflowThruInnerClass
