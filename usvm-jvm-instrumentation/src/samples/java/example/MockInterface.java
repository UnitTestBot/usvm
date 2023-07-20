package example;

public interface MockInterface {

    int intMock();

    String strMock();

    default int intMockDefault() {
        return 1;
    }

}
