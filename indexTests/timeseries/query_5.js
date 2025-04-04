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

                $or: [{consumption: {$exists: true}}, {production: {$exists: true}}],
            }
        },

        //computing the two means
        {
            $group:
            {
                _id: null,
                avgConsumption: {$avg: "$consumption"},
              	avgProduction: {$avg: "$production"}
            }
        },

        //compute percent
        {
          $addFields: {
            percent: {$divide: ["$avgProduction", "$avgConsumption"]}
          }
        },

        //project percent
  		{
  		    $project: {
  		        percent: 1
  		    }
  	    }
    ]
).explain("executionStats");