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
public class SensorService {
    
    String usersCollectionName = "Users";
    String buildingsCollection = "Buildings";
    String sensorsCollectionName = "Sensors";
    String readingsCollectionName = "Readings";

    // Descrizione:
    //  Aggiunge un sensore alla lista dei sensori dell’edificio, l’username deve essere l'admin dell’edificio
    // Collections:
    //  Sensors: aggiungi il sensore
    //  Buildings: aggiungi il sensore alla lista dei sensori dell’edificio
    // Risposta:
    //  String: messaggio di conferma
    public String addSensorToBuilding(AddSensorToBuildingRequest addSensorToBuildingRequest) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        // Accedi alle collections
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> sensorsCollection = database.getCollection(sensorsCollectionName);

        // Estrai informazioni
        String username = addSensorToBuildingRequest.getUsername();
        String sensorName = addSensorToBuildingRequest.getSensor().getName();
        String sensorType = addSensorToBuildingRequest.getSensor().getType();
        Integer sensorId = 0;
        Integer buildingId = addSensorToBuildingRequest.getSensor().getBuildingId();
        
        // Controlla che l'edificio esista e che l'admin sia admin
        Bson buildingFilter = Filters.eq("id", buildingId);
        Bson adminFilter = Filters.eq("admin", username);
        Bson filter = Filters.and(buildingFilter, adminFilter);
        Document foundBuilding = collection.find(filter).first();
        if (foundBuilding == null) {
            MyMongoConnection.closeConnection();
            Document notFoundBuilding = new Document("error", "Building does not exist or user is not admin");
            return notFoundBuilding.toJson();
        }
         
        // Calcola ID del sensore
        Document stage = new Document();
        stage.append("_id", null);
        stage.append("maxID", new Document("$max", "$id"));
        stage = new Document("$group", stage);

        AggregateIterable<Document> result = sensorsCollection.aggregate(
            Arrays.asList(stage)
        );
        if (result.first() != null) {
            Integer maxId = result.first().getInteger("maxID");
            sensorId = maxId + 1;
        } else {
            sensorId = 0;
        }

        // Aggiungi sensore alla collection sensori
        Document sensorDocument = new Document("name", sensorName)
            .append("type", sensorType)
            .append("id", sensorId)
            .append("buildingID", buildingId);
        sensorsCollection.insertOne(sensorDocument);

        // Aggiungi sensore alla lista di sensori dell'edificio
        Document embeddedSensorDocument = new Document("name", sensorName)
            .append("type", sensorType)
            .append("id", sensorId);
        Bson update = push("sensors", embeddedSensorDocument);
        collection.updateOne(filter, update);

        MyMongoConnection.closeConnection();
        return sensorDocument.toJson();
    }

    // Descrizione:
    //  Rimuove un sensore dalla lista dei sensori dell’edificio, l’username deve essere l'admin dell’edificio
    // Collections:
    //  Sensors: rimuovi il sensore
    //  Buildings: rimuovi il sensore dalla lista dei sensori dell’edificio
    //  Readings: rimuovi le letture del sensore
    // Risposta:
    //  String: messaggio di conferma
    public String removeSensorFromBuilding(Integer sensorId, Integer buildingId, String username) {
        
        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        if (sensorId == null || buildingId == null || username == null || username.isEmpty()) {
            return new Document("error", "invalid parameters").toJson();
        }
        // Accedi alle collections
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> sensorsCollection = database.getCollection(sensorsCollectionName);
        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        // Controlla che l'edificio esista e che l'admin sia admin
        Bson buildingFilter = Filters.eq("id", buildingId);
        Bson adminFilter = Filters.eq("admin", username);
        Bson filter = Filters.and(buildingFilter, adminFilter);
        Document foundBuilding = collection.find(filter).first();
        if (foundBuilding == null) {
            MyMongoConnection.closeConnection();
            Document notFoundBuilding = new Document("error", "Building does not exist or user is not admin");
            return notFoundBuilding.toJson();
        }

        // Controlla che il sensore esista e che sia nel building
        Bson sensorFilter = elemMatch("sensors", Filters.eq("id", sensorId));
        Document foundSensor = collection.find(sensorFilter).first();
        if (foundSensor == null) {
            MyMongoConnection.closeConnection();
            Document notFoundSensor = new Document("error", "Sensor does not exist or is not in building"); 
            return notFoundSensor.toJson();
        }

        // Rimuovi il sensore dalla collection sensori
        Bson filterSensor = Filters.eq("id", sensorId);
        sensorsCollection.deleteOne(filterSensor);

        // Rimuovi il sensore dalla lista di sensori dell'edificio
        Bson update = pull("sensors", new Document("id", sensorId));
        collection.updateMany(buildingFilter, update);

        // Rimuovi le letture del sensore
        Bson filterReadings = Filters.eq("sensorID", sensorId);
        readingsCollection.deleteMany(filterReadings);

        // Rimuovi le rilevazioni del sensore
        MyMongoConnection.closeConnection();
        Document removedSensor = new Document("id", sensorId);
        return removedSensor.toJson();

    }
    
}
