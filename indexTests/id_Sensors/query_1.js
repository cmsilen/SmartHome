db.Sensors.find(
    {
        id: 20
    }
).explain("executionStats");