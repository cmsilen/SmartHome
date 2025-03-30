package it.unipi.SmartHome.model;

// Java DTO (Data Transfer Object)
public class BuildingsResponse {

    private Building[] buildings;

    // Getters and Setters
    public Building[] getBuildings() { 
        return buildings; 
    }
    public void setBuildings(Building[] buildings) { 
        this.buildings = buildings; 
    }

}
