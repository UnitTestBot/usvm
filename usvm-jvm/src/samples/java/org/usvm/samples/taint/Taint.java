package org.usvm.samples.taint;

public class Taint {
    public void taintedEntrySource(String taintedVariable, String cleanVariable, boolean returnTainted) {
        String value;
        if (returnTainted) {
            value = taintedVariable;
        } else {
            value = cleanVariable;
        }

        consumerOfInjections(value);
    }

    public int simpleTaint(boolean x) {
        String value = stringProducer(x);

        consumerOfInjections(value);

        return value.length();
    }

    public int simpleFalsePositive(boolean x) {
        String value = stringProducer(x);
        String[] array = new String[2];

        array[0] = value;
        array[1] = "safe_data";

        consumerOfInjections(array[1]);

        return value.length();
    }

    public int simpleTruePositive(boolean x, int i) {
        String value = stringProducer(x);
        String[] array = new String[2];

        array[0] = value;
        array[1] = "safe_data";

        consumerOfInjections(array[i]);

        return value.length();
    }

    public int taintWithReturningValue(boolean x) {
        String value = stringProducer(x);

        return consumerWithReturningValue(value);
    }

    public void goThroughCleaner() {
        String value = stringProducer(true);

        String cleanData = cleaner(value);
        consumerOfInjections(cleanData);
    }

    // TODO add tests for PassThrough


    public void consumerOfInjections(String data) {
        // empty
    }

    public void consumerOfSensitiveData(String data) {
        // empty
    }

    public int consumerWithReturningValue(String data) {
        return 1;
    }


    public String cleaner(String data) {
        return data;
    }

    public String stringProducer(boolean produceTaint) {
        if (produceTaint) {
            return "taintedData";
        }

        return "";
    }
}
