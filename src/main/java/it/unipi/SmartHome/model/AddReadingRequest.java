package it.unipi.SmartHome.model;

public class AddReadingRequest {

    private Integer sensorId;
    private Integer buildingId;
    private String timestamp;
    private String username;
    private Float value1;
    private Float value2;
    private String type;

    public Integer getSensorId() {
        return sensorId;
    }
    public void setSensorId(Integer sensorId) {
        this.sensorId = sensorId;
    }
    public Integer getBuildingId() {
        return buildingId;
    }   
    public void setBuildingId(Integer buildingId) {
        this.buildingId = buildingId;
    }
    public String getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public Float getValue1() {
        return value1;
    }
    public void setValue1(Float value1) {
        this.value1 = value1;
    }
    public Float getValue2() {
        return value2;
    }
    public void setValue2(Float value2) {
        this.value2 = value2;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

}