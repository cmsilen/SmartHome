var buildingID = 20
var startTimestamp = '2016-01-01';
var endTimestamp = '2016-02-01';
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
                
                humidity: {
                    $exists: true
                }
            }
        },

        //computing the maximum per day
        {
            $group:
            {
                _id: { "$dateTrunc": { "date": "$timestamp", "unit": "day" } },
                maxHumidity: {$max: "$humidity"}
            }
        },

  		//sorting
        {
            $sort: { maxHumidity: -1}
        },

  		//maximum
        {
            $limit: 1
        }
    ]
).explain("executionStats");