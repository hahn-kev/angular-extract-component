import java.util.Objects;

public class InputField {
    public InputField(String fieldName, String fieldType, String originalBody) {
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.originalBody = originalBody;
    }

    public InputField(String fieldName, String fieldType, String originalBody, boolean isTwoWay) {
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.originalBody = originalBody;
        this.isTwoWay = isTwoWay;
    }

    public boolean isTwoWay = false;
    public String fieldName;
    public String fieldType;
    public String originalBody;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InputField that = (InputField) o;
        return Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName);
    }
}
