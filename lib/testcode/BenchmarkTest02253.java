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
 * @author Nick Sanidas
 * @created 2015
 */
package org.owasp.benchmark.testcode;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value = "/cmdi-02/BenchmarkTest02253")
public class BenchmarkTest02253 extends HttpServlet {

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

        java.util.Map<String, String[]> map = request.getParameterMap();
        String param = "";
        if (!map.isEmpty()) {
            String[] values = map.get("BenchmarkTest02253");
            if (values != null) param = values[0];
        }

        String bar = doSomething(request, param);

        String cmd = "";
        String a1 = "";
        String a2 = "";
        String[] args = null;
        String osName = System.getProperty("os.name");

        if (osName.indexOf("Windows") != -1) {
            a1 = "cmd.exe";
            a2 = "/c";
            cmd = "echo ";
            args = new String[] {a1, a2, cmd, bar};
        } else {
            a1 = "sh";
            a2 = "-c";
            cmd = org.owasp.benchmark.helpers.Utils.getOSCommandString("ls ");
            args = new String[] {a1, a2, cmd + bar};
        }

        String[] argsEnv = {"foo=bar"};

        Runtime r = Runtime.getRuntime();

        try {
            Process p = r.exec(args, argsEnv, new java.io.File(System.getProperty("user.dir")));
            org.owasp.benchmark.helpers.Utils.printOSCommandResults(p, response);
        } catch (IOException e) {
            System.out.println("Problem executing cmdi - TestCase");
            response.getWriter()
                    .println(org.owasp.esapi.ESAPI.encoder().encodeForHTML(e.getMessage()));
            return;
        }
    } // end doPost

    private static String doSomething(HttpServletRequest request, String param)
            throws ServletException, IOException {

        String bar;
        String guess = "ABC";
        char switchTarget = guess.charAt(1); // condition 'B', which is safe

        // Simple case statement that assigns param to bar on conditions 'A', 'C', or 'D'
        switch (switchTarget) {
            case 'A':
                bar = param;
                break;
            case 'B':
                bar = "bob";
                break;
            case 'C':
            case 'D':
                bar = param;
                break;
            default:
                bar = "bob's your uncle";
                break;
        }

        return bar;
    }
}
