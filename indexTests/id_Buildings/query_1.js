db.Buildings.find(
    {
        buildingID: 20
    }
).explain("executionStats");