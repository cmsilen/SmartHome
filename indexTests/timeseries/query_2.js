var buildingID = 20
var startTimestamp = '2016-02-01';
var endTimestamp = '2016-03-01';
db.Readings.aggregate(
    [
        //filtering readings
        {
            $match: {
                timestamp: {
                    $gte: new Date(startTimestamp),
                    $lt: new Date(endTimestamp)
                },
                buildingID: buildingID,

                consumption: {
                    $exists: true
                }
            }
        },
        //grouping the readings by sensor
        {
            $group: {
                _id: "$sensorID",
                avgPowerConsumption: {
                    $avg: "$consumption"
                }
            }
        },
        //sorting
        {
            $sort: {
                avgPowerConsumption: -1
            }
        },
        //top 5
        {
            $limit: 5
        }
    ]
).explain("executionStats");
