package it.unipi.SmartHome.database;

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
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

public class MyMongoConnection {

    // Variabili di connessione al database
    private static String databaseName = "SmartHome";
    private static ConnectionString uri = new ConnectionString("mongodb://localhost:27018");
    private static MongoClientSettings mcs = MongoClientSettings.builder()
        .applyConnectionString(uri)
        .readPreference(ReadPreference.nearest())
        .retryWrites(true)
        .writeConcern(WriteConcern.ACKNOWLEDGED)
        .build();
    
    private static MongoClient mongoClient;

    // Restituisce la connessione al database
    public static MongoDatabase getDatabase() {
        
        // Logga per utilita' di debug
        System.out.println("Connecting to MongoDB requested");
        
        // Se la connessione e' rimasta aperta la chiude
        if (mongoClient != null) {
            mongoClient.close();
        }

        // Crea una nuova connessione
        mongoClient = MongoClients.create(mcs);
        MongoDatabase database = mongoClient.getDatabase(databaseName);

        // Logga e ritorna la connessione al database 
        System.out.println("Connected to MongoDB");
        return database;
    }

    // Chiude la connessione al database
    public static void closeConnection() {

        // Logga per utilita' di debug e chiude la connessione
        System.out.println("Connection to MongoDB closed");
        if (mongoClient != null) {
            mongoClient.close();
        }

    }
    
}
