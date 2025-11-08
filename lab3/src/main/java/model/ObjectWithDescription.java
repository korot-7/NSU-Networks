package model;

public class ObjectWithDescription {
    private final ClosestObject closestObject;
    private final String description;

    public ObjectWithDescription(ClosestObject closestObject, String description) {
        this.closestObject = closestObject;
        this.description = description;
    }

    public ClosestObject getClosestObject() {
        return closestObject;
    }

    public String getDescription() {
        return description;
    }
}
