package it.unipi.SmartHome.model;

// Java DTO (Data Transfer Object)
public class BuildingsResponse {

    private BuildingResponse[] buildings;

    public BuildingsResponse(Integer n, String[] names, Integer[] ids) {
        this.buildings = new BuildingResponse[n];
        for (int i = 0; i < n; i++) {
            this.buildings[i] = new BuildingResponse(names[i], ids[i]);
        }
    }

}
