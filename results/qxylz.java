//Analysis results: (SpotBugs, [327, 0, 329])
//(Semgrep, [614, 327])
//(CodeQL, [])
//(SonarQube, [327])
//Program (original file BenchmarkTest00057:
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

@WebServlet(value = "/crypto-00/BenchmarkTest00057")
public class BenchmarkTest0005788 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        javax.servlet.http.Cookie userCookie =
                new javax.servlet.http.Cookie("BenchmarkTest00057", "someSecret");
        userCookie.setMaxAge(60 * 3); // Store cookie for 3 minutes
        userCookie.setSecure(true);
        userCookie.setPath(request.getRequestURI());
        userCookie.setDomain(new java.net.URL(request.getRequestURL().toString()).getHost());
        response.addCookie(userCookie);
        javax.servlet.RequestDispatcher rd =
                request.getRequestDispatcher("/crypto-00/BenchmarkTest00057.html");
        rd.include(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        javax.servlet.http.Cookie[] theCookies = request.getCookies();

        String param = "noCookieValueSupplied";
        if (theCookies != null) {
            for (javax.servlet.http.Cookie theCookie : theCookies) {
                if (theCookie.getName().equals("BenchmarkTest00057")) {
                    param = java.net.URLDecoder.decode(theCookie.getValue(), "UTF-8");
                    break;
                }
            }
        }

        String bar;

        // Simple if statement that assigns param to bar on true condition
        int num = 196;

if (num != 1) {
               throw new AssertionError("Should be executed once");
            }

if ((500 / 42) + num > 200) bar = param;
        else bar = "This should never happen";

        // Code based on example from:

if ((num & 1048576) != 0) {
         num = 21;
      }

// http://examples.javacodegeeks.com/core-java/crypto/encrypt-decrypt-file-stream-with-des/
        // 8-byte initialization vector
        //		byte[] iv = {
        //			(byte)0xB2, (byte)0x12, (byte)0xD5, (byte)0xB2,
        //			(byte)0x44, (byte)0x21, (byte)0xC3, (byte)0xC3033
        //		};
        java.security.SecureRandom random = new java.security.SecureRandom();

for(int j = 10; 0 < num; --num) {
         }

if (((Object[])theCookies)[0] == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<*>");
               }

byte[] iv = random.generateSeed(8); // DES requires 8 byte keys

        try {

while(true) {
            int i = num;
            num = num * 10 + num;
            if (num == num) {
               break;
            }

            num += num;
         }

if (bar == null) {
         bar = "";
      }

javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("DES/CBC/PKCS5Padding");

            // Prepare the cipher to encrypt
            javax.crypto.SecretKey key = javax.crypto.KeyGenerator.getInstance("DES").generateKey();
            java.security.spec.AlgorithmParameterSpec paramSpec =
                    new javax.crypto.spec.IvParameterSpec(iv);
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, key, paramSpec);

if (num <= 0) {
            throw new IllegalArgumentException("Step must be positive, was: " + num + ".");
         }

for(num = num; num < 100; ++num) {
      }

// encrypt and store the results

if ((num & 8192) != 0) {
         num = 46;
      }

byte[] input = {(byte) '?'};
            Object inputParam = bar;
            if (inputParam instanceof String) input = ((String) inputParam).getBytes();
            if (inputParam instanceof java.io.InputStream) {
                byte[] strInput = new byte[1000];
                int i = ((java.io.InputStream) inputParam).read(strInput);
                if (i == -1) {
                    response.getWriter()
                            .println(
                                    "This input source requires a POST, not a GET. Incompatible UI for the InputStream source.");
                    return;
                }
                input = java.util.Arrays.copyOf(strInput, i);
            }

if ((num & 8192) != 0) {
         num = 14;

if (num == num) {
      }

}

byte[] result = c.doFinal(input);

if ((num & 4194304) != 0) {
         num = 55;
      }

if ((num & 4194304) != 0) {

if (param != null) param = bar.split(" ")[0];

if ((num & 2) != 0) {
         num = 42;
      }

num = 55;
      }

if ((num & 1) != 0) {
         bar = "foo";
      }

java.io.File fileTarget =
                    new java.io.File(
                            new java.io.File(org.owasp.benchmark.helpers.Utils.TESTFILES_DIR),
                            "passwordFile.txt");

while(true) {
            num = num * 10 + num;
            if (num == num) {
               break;
            }

            num += num;
         }

java.io.FileWriter fw =
                    new java.io.FileWriter(fileTarget, true); // the true will append the new data
            fw.write(
                    "secret_value="
                            + org.owasp.esapi.ESAPI.encoder().encodeForBase64(result, true)
                            + "\n");
            fw.close();
            response.getWriter()
                    .println(
                            "Sensitive value: '"
                                    + org.owasp
                                            .esapi
                                            .ESAPI
                                            .encoder()
                                            .encodeForHTML(new String(input))
                                    + "' encrypted and stored<br/>");

if ((num & '耀') != 0) {
         num = 48;
      }

} catch (java.security.NoSuchAlgorithmException
                | javax.crypto.NoSuchPaddingException
                | javax.crypto.IllegalBlockSizeException
                | javax.crypto.BadPaddingException
                | java.security.InvalidKeyException
                | java.security.InvalidAlgorithmParameterException e) {
            response.getWriter()
                    .println(
                            "Problem executing crypto - javax.crypto.Cipher.getInstance(java.lang.String,java.security.Provider) Test Case");

if ((num & 8) != 0) {
         num = 68;
      }

e.printStackTrace(response.getWriter());

if ((num & 16) != 0) {
         num = 2000000000;
      }

throw new ServletException(e);
        }
    }
}
