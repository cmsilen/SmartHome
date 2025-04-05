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
public class BuildingService {
    

    String usersCollectionName = "Users";
    String buildingsCollection = "Buildings";
    String sensorsCollectionName = "Sensors";
    String readingsCollectionName = "Readings";
    
    // Funzioni di utilita'
    private boolean isBuildingValid(Building building) {
        return building.getName() != null && !building.getName().isEmpty() && building.getAdmin() != null;
    }

    // Descrizione:
    //   Aggiunge un nuovo edificio
    // Collections:
    //   Buildings: Inserisci nuovo building
    //   Users: Inserisci building nella lista degli edifici dell'utente
    // Risposta:
    //   String: messaggio di conferma
    public String addBuilding(Building building) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        if(!this.isBuildingValid(building)) {
            MyMongoConnection.closeConnection();
            return new Document("error", "invalid parameters").toJson();
        }
        
        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> usersCollection = database.getCollection(usersCollectionName);

        // Controllo che l'utente esista
        Bson filter = Filters.eq("username", building.getAdmin());
        Document foundUser = usersCollection.find(filter).first();
        if (foundUser == null) {
            Document adminNotFound = new Document("error", "Admin not found");
            MyMongoConnection.closeConnection();
            return adminNotFound.toJson();
        }
        
        // Calcolo l'id del nuovo edificio
        Document stage = new Document();
        stage.append("_id", null);
        stage.append("maxID", new Document("$max", "$id"));
        stage = new Document("$group", stage);

        AggregateIterable<Document> result = collection.aggregate(
            Arrays.asList(stage)
        );
        if (result.first() != null) {
            Integer maxId = result.first().getInteger("maxID");
            building.setId(maxId + 1);
        } else {
            building.setId(0);
        }

        // Inserisco il nuovo edificio
        Document buildingDocument = new Document("name", building.getName())
            .append("id", building.getId())
            .append("location", building.getLocation())
            .append("users", Arrays.asList(building.getAdmin()))
            .append("admin", building.getAdmin())
            .append("sensors", new org.bson.types.BasicBSONList());
        collection.insertOne(buildingDocument);
        
        // Aggiorno la collection degli utenti inserendo l'edificio
        Document newBuilding = new Document("buildingName", building.getName())
            .append("buildingID", building.getId());
        Bson update = push("buildings", newBuilding);
        usersCollection.updateOne(filter, update);

        // Invalido il KV DB
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "building:" + building.getAdmin() + ":buildings";
        if (jedis.exists(redisKey)) {
            jedis.del(redisKey);
        }
        MyRedisConnection.closeJedisCluster(jedis);

        MyMongoConnection.closeConnection();
        return buildingDocument.toJson();
    
    }

    // Descrizione:
    //   Rimuove un edificio
    // Collections:
    //   Users: Rimuovi edificio dalla lista degli edifici dell'utente
    //   Buildings: Rimuovi edificio
    //   Sensors: Rimuovi sensori dell'edificio
    //   Readings: Rimuovi letture del sensore
    // Risposta:
    //   String: messaggio di conferma
    public String removeBuilding(Integer id, String username) {
        
        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        if(id == null || username == null || username.isEmpty()) {
            MyMongoConnection.closeConnection();
            return new Document("result", "invalid parameters").toJson();
        }

        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> usersCollection = database.getCollection(usersCollectionName);
        MongoCollection<Document> sensorsCollection = database.getCollection(sensorsCollectionName);
        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        // Controllo che l'edificio esista
        Document foundBuilding = collection.find(
            Filters.eq("id", id)
        ).first();
        if (foundBuilding == null) {
            Document notFoundBuilding = new Document("error", "Building not found");    
            MyMongoConnection.closeConnection();
            return notFoundBuilding.toJson();
        }

        // Controllo che l'utente sia l'admin dell'edificio
        String admin = foundBuilding.getString("admin");
        if (!admin.equals(username)) {
            Document notFoundAdmin = new Document("error", "User is not admin of the building"); 
            MyMongoConnection.closeConnection();
            return notFoundAdmin.toJson();
        }

        // Cancello l'edificio dalla lista degli edifici degli utenti
        Bson update = pull("buildings", new Document("buildingID", id));
        Bson filter = elemMatch("buildings", Filters.eq("buildingID", id));
        usersCollection.updateMany(filter, update);
        // NOTA indice su buildingId

        // Cancello i sensori dell'edificio 
        filter = Filters.eq("buildingID", id);
        sensorsCollection.deleteMany(filter);

        // Rimuovi le letture del sensori
        Bson filterReadings = Filters.eq("buildingID", id);
        readingsCollection.deleteMany(filterReadings);

        // Elimino l'edificio
        collection.deleteOne(Filters.eq("id", id));

        // Invalido il KV DB
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "building:" + username + ":buildings";
        if (jedis.exists(redisKey)) {
            jedis.del(redisKey);
        }
        MyRedisConnection.closeJedisCluster(jedis);

        Document deletedBuilding = new Document("id", id);
        MyMongoConnection.closeConnection();
        return deletedBuilding.toJson();
    }

    // Descrizione:
    //  Aggiunge un utente alla lista degli utenti dell’edificio, l’username dell’admin deve essere
    //  l'admin dell’edificio
    // Collections:
    //   Users: Aggiungi edificio alla lista degli edifici dell'utente
    //   Buildings: Controlla se l'admin e' l'admin dell'edificio e aggiungi l'utente alla lista
    // Risposta:
    //   String: messaggio di conferma
    public String addUserToBuilding(String username, String admin, Integer id) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> usersCollection = database.getCollection(usersCollectionName);

        // Controllo che l'edificio esista
        Document foundBuilding = collection.find(
            Filters.eq("id", id)
        ).first();
        if (foundBuilding == null) {
            Document notFoundBuilding = new Document("error", "Building not found");
            MyMongoConnection.closeConnection();
            return notFoundBuilding.toJson();
        }

        // Controllo che gli utenti esistano
        //controllo sullo user
        Document foundUser = usersCollection.find(
                Filters.eq("username", username)
        ).first();
        if (foundUser == null) {
            Document notFoundUser = new Document("error", "User not found");
            MyMongoConnection.closeConnection();
            return notFoundUser.toJson();
        }

        //controllo sull'admin
        if(!foundBuilding.getString("admin").equals(admin)) {
            Document notFoundAdmin = new Document("error", "User is not admin of the building");
            MyMongoConnection.closeConnection();
            return notFoundAdmin.toJson();
        }

        // Controllo che l'utente non appartenga già all'edificio (i dati sono in building)
        List<String> usersInBuilding = foundBuilding.getList("users", String.class);
        boolean found = false;
        for (String user : usersInBuilding) {
            if(user.equals(username)) {
                found = true;
                break;
            }
        }
        if(found) {
            Document alreadyInBuilding = new Document("error", "User already in building");
            MyMongoConnection.closeConnection();
            return alreadyInBuilding.toJson();
        }

        // Aggiungo l'edificio alla lista dell'utente
        Bson userFilter = Filters.eq("username", username);
        String name = foundBuilding.getString("name");
        Document newBuilding = new Document("buildingName", name)
            .append("buildingID", id);
        Bson update = push("buildings", newBuilding);
        usersCollection.updateOne(userFilter, update);

        // Aggiungo l'utente alla lista dell'edificio 
        Bson filter = Filters.eq("id", id);
        update = push("users", username);
        collection.updateOne(
            filter,
            update
        );

        // Invalido il KV DB
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "building:" + username + ":buildings";
        if (jedis.exists(redisKey)) {
            jedis.del(redisKey);
        }
        MyRedisConnection.closeJedisCluster(jedis);

        MyMongoConnection.closeConnection();
        Document addedUser = new Document("username", username)
            .append("buildingID", id);
        return addedUser.toJson();
    }
    
}
