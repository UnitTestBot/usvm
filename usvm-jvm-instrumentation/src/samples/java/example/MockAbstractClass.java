package example;

public abstract class MockAbstractClass {

    public String stringField = "";
    public int intField = 238;


    public abstract int getI();

    public String getStr() { return "zzz"; }

    public int methodWithInternalInvocation() {
        return intField + getI();
    }


}
