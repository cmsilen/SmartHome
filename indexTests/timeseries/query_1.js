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

                precipitationIntensity: {
                    $exists: true
                }
            }
        },
        
        //computing the day in which each reading is performed
        {
            $project: {
                precipitationIntensity: 1,
                date: {
                    $dateToString: {format: "%d-%m-%Y", date: "$timestamp"}
                }
            }
        },
        
        //grouping the readings by day
        {
            $group: {
                _id: "$date",
                sumPrecipitation: {$sum: "$precipitationIntensity"}
            }
        },
        
        //removing days without rain
        {
            $match: {
                sumPrecipitation: {
                    $gt: 0
                }
            }
        },
        
        //counting rainy days
        {
            $group: {
                _id: "",
                count: {$sum: 1}
            }
        }
    ]
).explain("executionStats");
