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
import org.springframework.boot.autoconfigure.data.redis.RedisProperties.Jedis;
import org.springframework.stereotype.Service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
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
public class ReadingService {
    
    String usersCollectionName = "Users";
    String buildingsCollection = "Buildings";
    String sensorsCollectionName = "Sensors";
    String readingsCollectionName = "Readings";
    
    // Descrizione:
    //  Inserisce una lettura del sensore, l’username deve essere l'admin dell’edificio
    // Collections:
    //  Buildings: controlla che l'edificio esista, che l'admin sia admin
    //  Sensors: controlla che il sensore sia nell'edificio e prende il tipo
    //  Readings: inserisce la lettura
    // Risposta:
    //  String: messaggio di conferma
    public String addReading(AddReadingRequest request) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        if (request.getSensorId() == null || request.getBuildingId() == null) {
            return new Document("error", "invalid parameters").toJson();
        }
    
        // Accedi alle collections
        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);
        MongoCollection<Document> sensorsCollection = database.getCollection(sensorsCollectionName);

        // Estrai informazioni
        Integer sensorId = request.getSensorId();
        Integer buildingId = request.getBuildingId();
        Float value1 = request.getValue1();
        Float value2 = request.getValue2();

        // Controlla che il sensore esista e che sia nel building
        Bson sensorFilter = Filters.eq("id", sensorId);
        Bson buildingFilter = Filters.eq("buildingID", buildingId);
        Bson filter = Filters.and(sensorFilter, buildingFilter);
        Document foundSensor = sensorsCollection.find(filter).first();
        if (foundSensor == null) {
            MyMongoConnection.closeConnection();
            Document notFoundSensor = new Document("error", "Sensor does not exist or is not in building");
            return notFoundSensor.toJson();
        }


        // Aggiungi la lettura alla collection Readings, uso il tipo di sensore per decidere se aggiungere 
        // value1 e/o value2 e come chiamare i campi che li contengono
        // Inoltre aggiorno Redis
        Date date = new Date();
        Document readingDocument = new Document("sensorID", sensorId)
            .append("buildingID", buildingId)
            .append("timestamp", date);
        String redisKey = "reading:" + sensorId + ":last";
        Document redisValue = new Document();
        String type = foundSensor.getString("type");

        if (type.equals("PowerConsumption")) {
            readingDocument.append("consumption", value1);
            redisValue.append("consumption", value1);
        } 
        else if (type.equals("SolarPanel")) {
            readingDocument.append("production", value1); 
            redisValue.append("production", value1);
        } 
        else if (type.equals("Temperature")) {
            readingDocument.append("temperature", value1);
            redisValue.append("temperature", value1);
        }
        else if (type.equals("Humidity")) {
            readingDocument.append("apparentTemperature", value1);
            readingDocument.append("humidity", value2);
            redisValue.append("apparentTemperature", value1);
            redisValue.append("humidity", value2);
        } 
        else if (type.equals("Pressure")) {
            readingDocument.append("pressure", value1);
            redisValue.append("pressure", value1);
        } 
        else if (type.equals("Wind")) {
            readingDocument.append("windSpeed", value1);
            readingDocument.append("windBearing", value2);
            redisValue.append("windSpeed", value1);
            redisValue.append("windBearing", value2);
        } 
        else if (type.equals("Precipitation")) {
            readingDocument.append("precipitationIntensity", value1);
            readingDocument.append("precipitationProbability", value2);
            redisValue.append("precipitationIntensity", value1);
            redisValue.append("precipitationProbability", value2);
        } 
        else {
            MyMongoConnection.closeConnection();
            Document notFoundSensor = new Document("error", "Sensor type not supported");
            return notFoundSensor.toJson();
        }
        readingsCollection.insertOne(readingDocument);
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        jedis.set(redisKey, redisValue.toJson());
        MyRedisConnection.closeJedisCluster(jedis);

        MyMongoConnection.closeConnection();
        return new Document("result", "success").toJson();
    }
}
