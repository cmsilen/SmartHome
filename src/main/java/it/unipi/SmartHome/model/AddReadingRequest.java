package it.unipi.SmartHome.model;

public class AddReadingRequest {

    private Integer sensorId;
    private Integer buildingId;
    private Float value1;
    private Float value2;

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

}