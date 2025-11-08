package model;

public class ClosestObject {
    private final String name;
    private final String type;
    private final String subtype;

    public ClosestObject(String name, String type, String subtype) {
        this.name = name;
        this.type = type;
        this.subtype = subtype;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getSubtype(){
        return subtype;
    }
}