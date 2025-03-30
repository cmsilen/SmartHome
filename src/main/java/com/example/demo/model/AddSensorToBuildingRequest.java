package com.example.demo;

public class AddSensorToBuildingRequest {

    private Sensor sensor;
    private String username;

    public Sensor getSensor() {
        return sensor;
    }
    public void setSensor(Sensor sensor) {
        this.sensor = sensor;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }   
    
}