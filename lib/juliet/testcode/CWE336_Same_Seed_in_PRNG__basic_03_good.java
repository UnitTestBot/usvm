/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE336_Same_Seed_in_PRNG__basic_03.java
Label Definition File: CWE336_Same_Seed_in_PRNG__basic.label.xml
Template File: point-flaw-03.tmpl.java
*/
/*
* @description
* CWE: 336 Same Seed in PRNG
* Sinks:
*    GoodSink: no explicit seed specified
*    BadSink : hardcoded seed
* Flow Variant: 03 Control flow: if(5==5) and if(5!=5)
*
* */

package juliet.testcases;

import juliet.support.*;

import java.security.SecureRandom;

public class CWE336_Same_Seed_in_PRNG__basic_03_good extends AbstractTestCase
{
    public void bad() throws Throwable
    { return;}

    /* good1() changes 5==5 to 5!=5 */
    private void good1() throws Throwable
    {
        if (5 != 5)
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run */
            IO.writeLine("Benign, fixed string");
        }
        else
        {

            SecureRandom secureRandom = new SecureRandom();

            /* FIX: no explicit seed specified; produces far less predictable PRNG sequence */

            IO.writeLine("" + secureRandom.nextInt());
            IO.writeLine("" + secureRandom.nextInt());

        }
    }

    /* good2() reverses the bodies in the if statement */
    private void good2() throws Throwable
    {
        if (5 == 5)
        {
            SecureRandom secureRandom = new SecureRandom();
            /* FIX: no explicit seed specified; produces far less predictable PRNG sequence */
            IO.writeLine("" + secureRandom.nextInt());
            IO.writeLine("" + secureRandom.nextInt());
        }
    }

    public void good() throws Throwable
    {
        good1();
        good2();
    }

    /* Below is the main(). It is only used when building this testcase on
     * its own for testing or for building a binary to use in testing binary
     * analysis tools. It is not used when compiling all the juliet.testcases as one
     * application, which is how source code analysis tools are tested.
     */
    public static void main(String[] args) throws ClassNotFoundException,
           InstantiationException, IllegalAccessException
    {
        mainFromParent(args);
    }
}
