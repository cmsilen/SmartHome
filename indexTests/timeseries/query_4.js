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

                consumption: {
                    $exists: true
                }
            }
        },

        //grouping the readings by timestamp
        {
            $group:
            {
                _id: "$timestamp",
                totalConsumption: { $sum: "$consumption" }
            }
        },

        //sorting
        {
            $sort: { totalConsumption: -1}
        },

  		//maximum
        {
            $limit: 1
        }
    ]

).explain("executionStats");