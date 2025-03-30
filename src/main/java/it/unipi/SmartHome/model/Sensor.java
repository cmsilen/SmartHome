package it.unipi.SmartHome.model;

public class Sensor {

    private String id;
    private String name;
    private String type;
    private String buildingId;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getBuildingId() {
        return buildingId;
    }
    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }
}