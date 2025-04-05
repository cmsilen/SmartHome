package it.unipi.SmartHome.service;

import static com.mongodb.client.model.Filters.elemMatch;
import static com.mongodb.client.model.Updates.pull;
import static com.mongodb.client.model.Updates.push;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;

import it.unipi.SmartHome.model.AddReadingRequest;
import it.unipi.SmartHome.model.AddSensorToBuildingRequest;
import it.unipi.SmartHome.model.Building;
import it.unipi.SmartHome.model.User;
import it.unipi.SmartHome.database.MyMongoConnection;
import it.unipi.SmartHome.database.MyRedisConnection;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

@Service
public class AnalyticsService {

    private String readingsCollectionName = "readings";
    private String buildingsCollectionName = "buildings";
    private String usersCollectionName = "users";
    private String sensorsCollectionName = "sensors";

    // Descrizione:
    //   Calcola la statistica rainy days
    public Document getRainyDays(int buildingId, int yearNumber, int monthNumber) {

        // Get MongoDB Connection   
        MongoDatabase database = MyMongoConnection.getDatabase();

        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        Date startTimestamp;
        Date endTimestamp;
        String monthStart = monthNumber < 10 ? "0" + monthNumber : "" + monthNumber;
        String yearStart = "" + yearNumber;
        if (monthNumber == 12) {
            monthNumber = 1;
            yearNumber = yearNumber + 1;
        }
        else {
            monthNumber = monthNumber + 1;
        }
        String monthEnd = monthNumber < 10 ? "0" + monthNumber : "" + monthNumber;
        String yearEnd = "" + yearNumber;
        try {
            startTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").parse(yearStart + "-" + monthStart + "-01 00:00:00.000 UTC");
            endTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").parse(yearEnd + "-" + monthEnd + "-01 00:00:00.000 UTC");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Document stage1 = new Document();
        stage1.append("timestamp", new Document("$gte", startTimestamp).append("$lt", endTimestamp));
        stage1.append("buildingID", buildingId);
        stage1.append("precipitationIntensity", new Document("$exists", true));

        Document stage2 = new Document();
        stage2.append("precipitationIntensity", 1);
        stage2.append("date", new Document("$dateToString", new Document("format", "%d-%m-%Y").append("date", "$timestamp")));

        Document stage3 = new Document();
        stage3.append("_id", "$date");
        stage3.append("sumPrecipitation", new Document("$sum", "$precipitationIntensity"));
        stage3 = new Document("$group", stage3);

        Document stage4 = new Document("sumPrecipitation", new Document("$gt", 0));

        Document stage5 = new Document();
        stage5.append("_id", "");
        stage5.append("count", new Document("$sum", 1));
        stage5 = new Document("$group", stage5);

        AggregateIterable<Document> result = readingsCollection.aggregate(
                Arrays.asList(
                        Aggregates.match(stage1),
                        Aggregates.project(stage2),
                        stage3,
                        Aggregates.match(stage4),
                        stage5
                )
        );

        System.out.println("Rainy days quer");

        if(result.first() != null) {
            Document ret = result.first();
            MyMongoConnection.closeConnection();
            return ret;
        }

        Document ret = new Document();
        ret.append("_id", "");
        ret.append("count", 0);
        MyMongoConnection.closeConnection();
        return ret;
    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getRainyDaysCache(Integer buildingId, Integer yearNumber, Integer monthNumber) {
        
        // Controlla se c'e' nel KV DB
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":rainyDays";
        if (jedis.exists(redisKey)) {
            System.out.println("Rainy days found in Redis");
            String redisValue = jedis.get(redisKey);
            MyRedisConnection.closeJedisCluster(jedis);
            return redisValue;
        }
        
        // Altrimenti ritorna null (MISS)
        MyRedisConnection.closeJedisCluster(jedis);
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per i giorni di pioggia
    public void setRainyDaysCache(Integer buildingId, Integer yearNumber, Integer monthNumber, String value) {

        System.out.println("Setting rainy days in Redis");
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":rainyDays";
        jedis.set(redisKey, value);
        MyRedisConnection.closeJedisCluster(jedis);

    }

    // Descrizione:
    //   Calcola la statistica top 5 power consumption
    public JSONArray getTop5PowerConsumption(int buildingId, int yearNumber, int monthNumber, int dayNumber) {
        
        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        String dayStart = dayNumber < 10 ? "0" + dayNumber : "" + dayNumber;
        String monthStart = monthNumber < 10 ? "0" + monthNumber : "" + monthNumber;
        String yearStart = "" + yearNumber;
        Date startTimestamp;
        Date endTimestamp;

        try {
            startTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").parse(yearStart + "-" + monthStart + "-" + dayStart + " 00:00:00.000 UTC");
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startTimestamp);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            endTimestamp = calendar.getTime();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Document stage1 = new Document();
        stage1.append("timestamp", new Document("$gte", startTimestamp).append("$lt", endTimestamp));
        stage1.append("buildingID", buildingId);
        stage1.append("consumption", new Document("$exists", true));
        stage1 = new Document("$match", stage1);

        Document stage2 = new Document();
        stage2.append("_id", "$sensorID");
        stage2.append("avgPowerConsumption", new Document("$avg", "$consumption"));
        stage2 = new Document("$group", stage2);

        Document stage3 = new Document("$sort", new Document("avgPowerConsumption", -1));

        Document stage4 = new Document("$limit", 5);

        AggregateIterable<Document> result = readingsCollection.aggregate(
                Arrays.asList(
                        stage1,
                        stage2,
                        stage3,
                        stage4
                )
        );

        JSONArray ret = new JSONArray();
        if(result.first() != null){
            for(Document d : result){
                ret.put(d);
            }
        }

        MyMongoConnection.closeConnection();
        return ret;
    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getTop5PowerConsumptionCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber) {
        
        // Controlla se c'e' nel KV DB
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":top5PowerConsumption";
        if (jedis.exists(redisKey)) {
            System.out.println("Top 5 Power Consumption found in Redis");
            String redisValue = jedis.get(redisKey);
            MyRedisConnection.closeJedisCluster(jedis);
            return redisValue;
        }
        
        // Altrimenti ritorna null (MISS)
            MyRedisConnection.closeJedisCluster(jedis);
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setTop5PowerConsumptionCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber, String value) {

        System.out.println("Setting Top 5 Power Consumption in Redis");
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":top5PowerConsumption";
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        jedis.set(redisKey, value);
        MyRedisConnection.closeJedisCluster(jedis);

    }

    // Descrizione
    //   Calcola la statistica peak temperature
    public JSONArray getPeakTemperature(int buildingId, int yearNumber, int monthNumber, int dayNumber) {
        
        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        String dayStart = dayNumber < 10 ? "0" + dayNumber : "" + dayNumber;
        String monthStart = monthNumber < 10 ? "0" + monthNumber : "" + monthNumber;
        String yearStart = "" + yearNumber;
        Date startTimestamp;
        Date endTimestamp;

        try {
            startTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").parse(yearStart + "-" + monthStart + "-" + dayStart + " 00:00:00.000 UTC");
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startTimestamp);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            endTimestamp = calendar.getTime();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Document stage1 = new Document();
        stage1.append("timestamp", new Document("$gte", startTimestamp).append("$lt", endTimestamp));
        stage1.append("buildingID", buildingId);
        stage1.append("temperature", new Document("$exists", true));
        stage1 = new Document("$match", stage1);

        Document stage2 = new Document();
        stage2.append("_id", "$timestamp");
        stage2.append("maxTemp", new Document("$max", "$temperature"));
        stage2 = new Document("$group", stage2);

        Document stage3 = new Document("$sort", new Document("maxTemp", -1));

        Document stage4 = new Document("$limit", 1);

        AggregateIterable<Document> result = readingsCollection.aggregate(
                Arrays.asList(
                        stage1,
                        stage2,
                        stage3,
                        stage4
                )
        );

        JSONArray ret = new JSONArray();
        if(result.first() != null){
            for(Document d : result){
                ret.put(d);
            }
        }

        MyMongoConnection.closeConnection();
        return ret;
    }

    // Descrizione
    //  Controlla se la statistica e' nel KV DB
    public String getPeakTemperatureCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber) {
        
        // Controlla se c'e' nel KV DB
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":peakTemperature";
        if (jedis.exists(redisKey)) {
            System.out.println("Peak Temperature found in Redis");
            String redisValue = jedis.get(redisKey);
            MyRedisConnection.closeJedisCluster(jedis);
            return redisValue;
        }
        
        // Altrimenti ritorna null (MISS)
        MyRedisConnection.closeJedisCluster(jedis);
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setPeakTemperatureCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber, String value) {

        System.out.println("Setting Peak Temperature in Redis");
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":peakTemperature";
        jedis.set(redisKey, value);
        MyRedisConnection.closeJedisCluster(jedis);

    }

    // Descrizione:
    //   Calcola la statistica peak power consumption
    public JSONArray getPeakPowerConsumptionHours(int buildingId, int yearNumber, int monthNumber, int dayNumber) {
        
        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        String dayStart = dayNumber < 10 ? "0" + dayNumber : "" + dayNumber;
        String monthStart = monthNumber < 10 ? "0" + monthNumber : "" + monthNumber;
        String yearStart = "" + yearNumber;
        Date startTimestamp;
        Date endTimestamp;

        try {
            startTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").parse(yearStart + "-" + monthStart + "-" + dayStart + " 00:00:00.000 UTC");
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startTimestamp);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            endTimestamp = calendar.getTime();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Document stage1 = new Document();
        stage1.append("timestamp", new Document("$gte", startTimestamp).append("$lt", endTimestamp));
        stage1.append("buildingID", buildingId);
        stage1.append("consumption", new Document("$exists", true));
        stage1 = new Document("$match", stage1);

        Document stage2 = new Document();
        stage2.append("_id", "$timestamp");
        stage2.append("totalConsumption", new Document("$sum", "$consumption"));
        stage2 = new Document("$group", stage2);

        Document stage3 = new Document("$sort", new Document("consumption", -1));

        Document stage4 = new Document("$limit", 1);

        AggregateIterable<Document> result = readingsCollection.aggregate(
                Arrays.asList(
                        stage1,
                        stage2,
                        stage3,
                        stage4
                )
        );

        JSONArray ret = new JSONArray();
        if(result.first() != null){
            for(Document d : result){
                ret.put(d);
            }
        }

        MyMongoConnection.closeConnection();
        return ret;
    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getPeakPowerConsumptionHoursCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber) {
        
        // Controlla se c'e' nel KV DB
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":peakPowerConsumption";
        if (jedis.exists(redisKey)) {
            System.out.println("Peak Power Consumption found in Redis");
            String redisValue = jedis.get(redisKey);
            MyRedisConnection.closeJedisCluster(jedis);
            return redisValue;
        }
        
        // Altrimenti ritorna null (MISS)
        MyRedisConnection.closeJedisCluster(jedis);
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setPeakPowerConsumptionHoursCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber, String value) {

        System.out.println("Setting Peak Power Consumption in Redis");
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":peakPowerConsumption";
        jedis.set(redisKey, value);
        MyRedisConnection.closeJedisCluster(jedis);

    }

    // Descrizione:
    //   Calcola la statistica percentage of power from solar panels
    public Document getPercentageOfPowerFromSolarPanels(int buildingId, int yearNumber, int monthNumber) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        Date startTimestamp;
        Date endTimestamp;
        String monthStart = monthNumber < 10 ? "0" + monthNumber : "" + monthNumber;
        String yearStart = "" + yearNumber;
        if (monthNumber == 12) {
            monthNumber = 1;
            yearNumber = yearNumber + 1;
        }
        else {
            monthNumber = monthNumber + 1;
        }
        String monthEnd = monthNumber < 10 ? "0" + monthNumber : "" + monthNumber;
        String yearEnd = "" + yearNumber;
        try {
            startTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").parse(yearStart + "-" + monthStart + "-01 00:00:00.000 UTC");
            endTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").parse(yearEnd + "-" + monthEnd + "-01 00:00:00.000 UTC");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        Document stage1 = new Document();
        stage1.append("timestamp", new Document("$gte", startTimestamp).append("$lt", endTimestamp));
        stage1.append("buildingID", buildingId);
        stage1.append("$or", List.of(new Document("consumption", new Document("$exists", true)),
                new Document("production", new Document("$exists", true)))
        );


        Document stage2 = new Document();
        stage2.append("_id", "");
        stage2.append("totalPowerConsumption", new Document("$sum", "$consumption"));
        stage2.append("totalPowerProduction", new Document("$sum", "$production"));
        stage2 = new Document("$group", stage2);


        Document stage3 = new Document();
        Document condition = new Document("if", new Document("$eq", List.of("$totalPowerConsumption", 0))).append("then", null)
                                .append("else", new Document("$divide", List.of("$totalPowerProduction", "$totalPowerConsumption")));
        stage3 = new Document("$project", new Document("percent", new Document("$cond", condition)));

        AggregateIterable<Document> result = readingsCollection.aggregate(
            Arrays.asList(
                Aggregates.match(stage1),
                stage2,
                stage3
            )
        );

        Document ret = null;
        if(result.first() != null) {
            ret = new Document("percentage", 
                100 * result.first().getDouble("percent")
            );
        }
        else {
            ret = new Document("percentage", 0);
        }
        MyMongoConnection.closeConnection();

        return ret;

    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getPercentageOfPowerFromSolarPanelsCache(Integer buildingId, Integer yearNumber, Integer monthNumber) {
        
        // Controlla se c'e' nel KV DB
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":percentageOfPowerFromSolarPanels";
        if (jedis.exists(redisKey)) {
            System.out.println("Percentage of Power from Solar Panels found in Redis");
            String redisValue = jedis.get(redisKey);
            MyRedisConnection.closeJedisCluster(jedis);
            return redisValue;
        }
        
        // Altrimenti ritorna null (MISS)
        MyRedisConnection.closeJedisCluster(jedis);
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setPercentageOfPowerFromSolarPanelsCache(Integer buildingId, Integer yearNumber, Integer monthNumber, String value) {

        System.out.println("Setting Percentage of Power from Solar Panels in Redis");
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":percentageOfPowerFromSolarPanels";
        jedis.set(redisKey, value);
        MyRedisConnection.closeJedisCluster(jedis);

    }

    // Descrizione:
    //   Calcola la statistica most humid day
    public Document getMostHumidDay(Integer buildingId, Integer yearNumber, Integer monthNumber) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();
        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        Date startTimestamp;
        Date endTimestamp;
        String monthStart = monthNumber < 10 ? "0" + monthNumber : "" + monthNumber;
        String yearStart = "" + yearNumber;
        if (monthNumber == 12) {
            monthNumber = 1;
            yearNumber = yearNumber + 1;
        }
        else {
            monthNumber = monthNumber + 1;
        }
        String monthEnd = monthNumber < 10 ? "0" + monthNumber : "" + monthNumber;
        String yearEnd = "" + yearNumber;
        try {
            startTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").parse(yearStart + "-" + monthStart + "-01 00:00:00.000 UTC");
            endTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").parse(yearEnd + "-" + monthEnd + "-01 00:00:00.000 UTC");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Filtra i documenti in base al timestamp, all'ID dell'edificio e al fatto che abbiano umidità
        Document stage1 = new Document();
        stage1.append("timestamp", new Document("$gte", startTimestamp).append("$lt", endTimestamp));
        stage1.append("buildingID", buildingId);
        stage1.append("humidity", new Document("$exists", true));


        // Raggruppa per giorno e calcola l'umidità massima
        Document stage2 = new Document();
        stage2.append("_id", new Document("$dateTrunc", new Document("date", "$timestamp").append("unit", "day")));
        stage2.append("maxHumidity", new Document("$max", "$humidity"));
        stage2 = new Document("$group", stage2);

        Document stage3 = new Document();
        stage3.append("$sort", new Document("maxHumidity", -1));

        Document stage4 = new Document("$limit", 1);

        AggregateIterable<Document> result = readingsCollection.aggregate(
                Arrays.asList(
                        Aggregates.match(stage1),
                        stage2,
                        stage3,
                        stage4
                )
        );

        Document ret = null;
        if(result.first() != null) 
            ret = result.first();
        else {
            ret = new Document();
            ret.append("_id", "");
            ret.append("count", 0);
        }
        MyMongoConnection.closeConnection();    
        return ret;

    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getMostHumidDayCache(Integer buildingId, Integer yearNumber, Integer monthNumber) {
        
        // Controlla se c'e' nel KV DB
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":mostHumidDay";
        if (jedis.exists(redisKey)) {
            System.out.println("Most Humid Day found in Redis");
            String redisValue = jedis.get(redisKey);
            MyRedisConnection.closeJedisCluster(jedis);
            return redisValue;
        }
        
        // Altrimenti ritorna null (MISS)
        MyRedisConnection.closeJedisCluster(jedis);
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setMostHumidDayCache(Integer buildingId, Integer yearNumber, Integer monthNumber, String value) {

        System.out.println("Setting Most Humid Day in Redis");
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":mostHumidDay";
        jedis.set(redisKey, value);
        MyRedisConnection.closeJedisCluster(jedis);

    }
}