package scaputo88.com.example.rinha_25.model;


public enum ProcessorType {
    DEFAULT("default"),
    FALLBACK("fallback");

    private final String value;

    ProcessorType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
