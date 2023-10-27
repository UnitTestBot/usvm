package example;

public class SleepingClass {
    public static void sleepFor(Long timeInMillis) throws InterruptedException {
        Thread.sleep(timeInMillis);
    }
}
