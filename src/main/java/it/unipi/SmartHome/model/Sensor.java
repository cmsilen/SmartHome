package it.unipi.SmartHome.model;

public class Sensor {

    private Integer id;
    private String name;
    private String type;
    private Integer buildingId;

    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
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
    public Integer getBuildingId() {
        return buildingId;
    }
    public void setBuildingId(Integer buildingId) {
        this.buildingId = buildingId;
    }
}