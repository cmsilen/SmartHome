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

import javax.print.Doc;

@Service
public class UserService {

    // Parametri di connessione a MongoDB
    String usersCollectionName = "Users";
    String buildingsCollection = "Buildings";
    String sensorsCollectionName = "Sensors";
    String readingsCollectionName = "Readings";


    // Funzioni di utilita'
    private boolean isUserValid(User user) {
        return user.getUsername() != null && !user.getUsername().isEmpty() && user.getPassword() != null && !user.getPassword().isEmpty() &&
                user.getName() != null && !user.getName().isEmpty() && user.getSurname() != null && !user.getSurname().isEmpty();
    }


    // Descrizione:
    //   Registrazione di un nuovo utente
    // Collections:
    //   Users: Inserisci nuovo utente
    // Risposta:
    //   JSON: Utente creato
    public String signUpUser(User user) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        // Valido i parametri
        if(!this.isUserValid(user)) {
            MyMongoConnection.closeConnection();
            return new Document("error", "invalid parameters").toJson();
        }

        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(usersCollectionName);

        // Controllo che non ci sia gia' un utente con lo stesso username
        Document foundUser = collection.find(Filters.eq("username", user.getUsername())).first();
        if (foundUser != null) {
            Document notFoundError = new Document("error", "User already exists");
            MyMongoConnection.closeConnection();
            return notFoundError.toJson();
        }
        // NOTA indice su username

        // Inserisco il nuovo utente
        Document userDocument = new Document("username", user.getUsername())
            .append("name", user.getName())
            .append("surname", user.getSurname())
            .append("password", user.getPassword())
            .append("buildings", new org.bson.types.BasicBSONList());
        collection.insertOne(userDocument);
        MyMongoConnection.closeConnection();
        return userDocument.toJson();

    }

    // Descrizione:
    //   Effettua il login dell’utente
    // Collections:
    //   Users: Controlla se l'utente esiste e se la password e' corretta
    // Risposta:
    //   JSON: Utente trovato
    public String loginUser(String username, String password) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        if(username == null || password == null || username.isEmpty() || password.isEmpty()) {
            MyMongoConnection.closeConnection();
            return new Document("error", "invalid parameters").toJson();
        }
        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(usersCollectionName);

        // Controllo che username e password siano corretti
        Document foundUser = collection.find(Filters.and(
            Filters.eq("username", username),
            Filters.eq("password", password)
        )).first();

        // Se l'utente esiste e la password e' corretta ritorno un messaggio di conferma altrimenti di errore
        MyMongoConnection.closeConnection();
        if (foundUser != null) {
            return foundUser.toJson();
        } else {
            Document notFoundUser = new Document("error", "User not found or password incorrect");
            return notFoundUser.toJson();
        }
    }


    // Descrizione:
    //   Ottiene la lista degli edifici dell'utente
    // Collections:
    //   Users: Ottiene la lista degli edifici dell'utente
    // Risposta:
    //   Lista degli edifici dell'utente
    public String getUserBuildings(String username) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        if(username == null || username.isEmpty()) {
            MyMongoConnection.closeConnection();
            return new Document("result", "invalid parameters").toJson();
        }

        // Ottieni la Collection
        MongoCollection<Document> usersCollection = database.getCollection(usersCollectionName);

        // Controlla se il risultato e' cachato su redis
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        String redisKey = "building:" + username + ":buildings";
        if (jedis.exists(redisKey)) {
            System.out.println("Buildings found in Redis");
            MyMongoConnection.closeConnection();
            MyRedisConnection.closeJedisCluster(jedis);
            String redisValue = jedis.get(redisKey);
            return redisValue;
        }

        // Controlla che l'utente esista 
        Bson filter = Filters.eq("username", username);
        Document foundUser = usersCollection.find(filter).first();
        if (foundUser == null) {
            MyMongoConnection.closeConnection();
            return new Document("error", "user does not exist").toJson();
        }

        // Leggi e concatena gli edifici
        List<Document> buildings = foundUser.getList("buildings", Document.class);
        List<Document> buildingsList = new ArrayList<>();
        for (Document building : buildings) {
            String name = building.getString("buildingName");
            Integer id = building.getInteger("buildingID");
            buildingsList.add(
                new Document()
                    .append("name", name)
                    .append("id", id)
            );
        }

        MyMongoConnection.closeConnection();
        Document responseDocument = new Document("buildings", buildingsList);
        jedis.set(redisKey, responseDocument.toJson());
        MyRedisConnection.closeJedisCluster(jedis);
        return responseDocument.toJson();

    }


    // Descrizione:
    //  Ottiene la lista dei sensori degli edifici dell'utente 
    // Collections:
    //  Buildings: ottiene la lista dei sensori degli edifici dell'utente 
    //  Readings: se serve ottiene le ultime letture dei sensori
    // Risposta:
    //  String: lista dei sensori
    public String getUserSensors(String username, Integer buildingID) {

        // Get MongoDB Connection
        MongoDatabase database = MyMongoConnection.getDatabase();

        if (username == null || buildingID == null) {
            MyMongoConnection.closeConnection();
            return new Document("error", "invalid parameters").toJson();
        }
    
        // Ottieni la Collection
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        // Controlla che l'utente sia nel building
        Bson buildingFilter = Filters.eq("id", buildingID);
        Document userFilter = new Document("users", 
            new Document("$in", Arrays.asList(username))
        );
        Document foundBuilding = collection.find(
            Filters.and(buildingFilter, userFilter)
        ).first();
        if (foundBuilding == null) {
            MyMongoConnection.closeConnection();
            Document notFoundBuilding = new Document("error", "Building does not exist or user is not in building");
            return notFoundBuilding.toJson();
        }

        // Crea lista dei sensori
        List<Document> sensors = foundBuilding.getList("sensors", Document.class);

        // Per ogni sensore ottieni l'ultima lettura
        JSONArray result = new JSONArray();
        JedisCluster jedis = MyRedisConnection.connectToJedisCluster();
        for (Document sensor : sensors) {
            Document currentSensor = new Document("sensorID", sensor.get("id")).append("name", sensor.get("name"));

            // Controlla se c'e' nel KV DB 
            String jedisKey = "reading:" + sensor.getInteger("id") + ":last";
            if (jedis.exists(jedisKey)) {
                System.out.println("Reading found in Redis");
                String reading = jedis.get(jedisKey);
                currentSensor.append("lastReading", Document.parse(reading));
                result.put(currentSensor);
                continue;
            }

            // Altrimenti la va a prendere da MongoDB
            Bson filter = Filters.eq("sensorID", sensor.getInteger("id"));
            Document foundReading = readingsCollection.find(filter).sort(new Document("timestamp", -1)).first();
            Document currentReading;

            if(foundReading == null) {
                result.put(currentSensor);
                continue;
            }
            if (foundReading.containsKey("consumption")) {
                System.out.println(1);
                Double consumption = getReadingData(foundReading, "consumption");
                currentReading = new Document("consumption", consumption);
            }
            else if (foundReading.containsKey("production")) {
                System.out.println(2);
                Double production = getReadingData(foundReading, "production");
                currentReading = new Document("production", production);
            }
            else if (foundReading.containsKey("temperature")) {
                System.out.println(3);
                Double temperature = getReadingData(foundReading, "temperature");
                currentReading = new Document("temperature", temperature);
            }
            else if (foundReading.containsKey("apparentTemperature")) {
                System.out.println(4);
                Double apparentTemperature = getReadingData(foundReading, "apparentTemperature");
                Double humidity = getReadingData(foundReading, "humidity");
                currentReading = new Document("apparentTemperature", apparentTemperature).append("humidity", humidity);
            }
            else if (foundReading.containsKey("pressure")) {
                System.out.println(5);
                Double pressure = getReadingData(foundReading, "pressure");
                currentReading = new Document("pressure", pressure);
            }
            else if (foundReading.containsKey("windSpeed")) {
                System.out.println(6);
                Double windSpeed = getReadingData(foundReading, "windSpeed");
                Double windBearing = getReadingData(foundReading, "windBearing");
                currentReading = new Document("windSpeed", windSpeed).append("windBearing", windBearing);
            }
            else if (foundReading.containsKey("precipitationIntensity")) {
                System.out.println(7);
                Double precipitationIntensity = getReadingData(foundReading, "precipitationIntensity");
                Double precipitationProbability = getReadingData(foundReading, "precipitationProbability");
                currentReading = new Document("precipitationIntensity", precipitationIntensity).append("precipitationProbability", precipitationProbability);
            }
            else {
                System.out.println(8);
                currentReading = new Document("error", "Sensor type not supported");
            }
            currentSensor.append("lastReading", currentReading);
            jedis.set(jedisKey, currentReading.toJson());
            result.put(currentSensor);
        }
        
        MyMongoConnection.closeConnection();
        MyRedisConnection.closeJedisCluster(jedis);
        return result.toString(4);
    }
    
    // Helper Function
    // Serve perché se il campo e' 0 allora per mongoDb e' un intero e se provi a leggere un double
    // ti da errore
    private Double getReadingData(Document reading, String field) {
        Object value = reading.get(field);
        Double result = null;
        if (value instanceof Double) {
            result = (Double) value;
        } else if (value instanceof Integer) {
            result = ((Integer) value).doubleValue();
        } else {
            System.out.println("Reading is of unknown type");
        }
        return result;
    }


}