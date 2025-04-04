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

@Service
public class UserService {

    // NOTA si potrebbero usare delle utility function tipo utenteEsiste(username) oppure edificioEsiste(id)
    // TODO aggiungere KV in GET /sensors
    // TODO aggiornare KV in POST /reading 
    // NICE TO HAVE: fare delle funzioni di utility per evitare codice copy paste nelle query (ma anche sti cazzi)


    // Parametri di connessione a MongoDB
    String usersCollectionName = "Users";
    String buildingsCollection = "Buildings";
    String sensorsCollectionName = "Sensors";
    String readingsCollectionName = "Readings";
    String dbName = "SmartHome";

    // Connessione a MongoDB
    //ConnectionString uri = new ConnectionString("mongodb://localhost:27017");

    // Connessione al cluster di MongoDB 
    ConnectionString uri = new ConnectionString("mongodb://localhost:27018");
    MongoClientSettings mcs = MongoClientSettings.builder()
        .applyConnectionString(uri)
        .readPreference(ReadPreference.nearest())
        .retryWrites(true)
        .writeConcern(WriteConcern.ACKNOWLEDGED)
        .build();
    
    MongoClient mongoClient = MongoClients.create(uri);
    MongoDatabase database = mongoClient.getDatabase(dbName);

    // Parametri di connessione a Redis
    String redisHost = "localhost";
    Integer redisPort = 6379;

    // Connessione a Redis
    // Jedis jedis = new Jedis(redisHost, redisPort);
    JedisCluster jedis = connectToJedisCluster();

    // Secondo me quetsa roba e' una maialata pero' per ora teniamola cosi'

    // FUNZIONI DI UTILITY
    private boolean isUserValid(User user) {
        return user.getUsername() != null && !user.getUsername().isEmpty() && user.getPassword() != null && !user.getPassword().isEmpty() &&
                user.getName() != null && !user.getName().isEmpty() && user.getSurname() != null && !user.getSurname().isEmpty();
    }
    private boolean isBuildingValid(Building building) {
        return building.getName() != null && !building.getName().isEmpty() && building.getAdmin() != null;
        // ho tolto building.getAdmin().isEmpty()
    }

    private JedisCluster connectToJedisCluster() {

        Set<HostAndPort> jedisClusterNodes = new HashSet<HostAndPort>();
        jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7001));
        jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7002));
        jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7003));

        JedisCluster jedis = new JedisCluster(jedisClusterNodes);
        return jedis;

    }


    // Descrizione:
    //   Registrazione di un nuovo utente
    // Collections:
    //   Users: Inserisci nuovo utente
    // Risposta:
    //   JSON: Utente creato
    public String signUpUser(User user) {

        // Valido i parametri
        if(!this.isUserValid(user)) {
            return new Document("error", "invalid parameters").toJson();
        }

        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(usersCollectionName);

        // Controllo che non ci sia gia' un utente con lo stesso username
        Document foundUser = collection.find(Filters.eq("username", user.getUsername())).first();
        if (foundUser != null) {
            Document notFoundError = new Document("error", "User already exists");
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
        return userDocument.toJson();

    }

    // Descrizione:
    //   Effettua il login dell’utente
    // Collections:
    //   Users: Controlla se l'utente esiste e se la password e' corretta
    // Risposta:
    //   JSON: Utente trovato
    public String loginUser(String username, String password) {

        if(username == null || password == null || username.isEmpty() || password.isEmpty()) {
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
        if (foundUser != null) {
            return foundUser.toJson();
        } else {
            Document notFoundUser = new Document("error", "User not found or password incorrect");
            return notFoundUser.toJson();
        }
    }

    // Descrizione:
    //   Aggiunge un nuovo edificio
    // Collections:
    //   Buildings: Inserisci nuovo building
    //   Users: Inserisci building nella lista degli edifici dell'utente
    // Risposta:
    //   String: messaggio di conferma
    public String addBuilding(Building building) {

        if(!this.isBuildingValid(building)) {
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
        String redisKey = "building:" + building.getAdmin() + ":buildings";
        if (jedis.exists(redisKey)) {
            jedis.del(redisKey);
        }

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
        
        if(id == null || username == null || username.isEmpty()) {
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
            return notFoundBuilding.toJson();
        }

        // Controllo che l'utente sia l'admin dell'edificio
        String admin = foundBuilding.getString("admin");
        if (!admin.equals(username)) {
            Document notFoundAdmin = new Document("error", "User is not admin of the building"); 
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
        String redisKey = "building:" + username + ":buildings";
        if (jedis.exists(redisKey)) {
            jedis.del(redisKey);
        }

        Document deletedBuilding = new Document("id", id);
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

        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> usersCollection = database.getCollection(usersCollectionName);

        // Controllo che l'edificio esista
        Document foundBuilding = collection.find(
            Filters.eq("id", id)
        ).first();
        if (foundBuilding == null) {
            Document notFoundBuilding = new Document("error", "Building not found");
            return notFoundBuilding.toJson();
        }

        // Controllo che gli utenti esistano
        //controllo sullo user
        Document foundUser = usersCollection.find(
                Filters.eq("username", username)
        ).first();
        if (foundUser == null) {
            Document notFoundUser = new Document("error", "User not found");
            return notFoundUser.toJson();
        }

        //controllo sull'admin
        if(!foundBuilding.getString("admin").equals(admin)) {
            Document notFoundAdmin = new Document("error", "User is not admin of the building");
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
        String redisKey = "building:" + username + ":buildings";
        if (jedis.exists(redisKey)) {
            jedis.del(redisKey);
        }

        Document addedUser = new Document("username", username)
            .append("buildingID", id);
        return addedUser.toJson();
    }

    // Descrizione:
    //   Ottiene la lista degli edifici dell'utente
    // Collections:
    //   Users: Ottiene la lista degli edifici dell'utente
    // Risposta:
    //   Lista degli edifici dell'utente
    public String getUserBuildings(String username) {
        if(username == null || username.isEmpty()) {
            return new Document("result", "invalid parameters").toJson();
        }

        // Ottieni la Collection
        MongoCollection<Document> usersCollection = database.getCollection(usersCollectionName);

        // Controlla se il risultato e' cachato su redis
        String redisKey = "building:" + username + ":buildings";
        if (jedis.exists(redisKey)) {
            System.out.println("Buildings found in Redis");
            return jedis.get(redisKey);
        }

        // Controlla che l'utente esista 
        Bson filter = Filters.eq("username", username);
        Document foundUser = usersCollection.find(filter).first();
        if (foundUser == null) {
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
        Document responseDocument = new Document("buildings", buildingsList);
        jedis.set(redisKey, responseDocument.toJson());
        return responseDocument.toJson();

    }

    // Descrizione:
    //  Aggiunge un sensore alla lista dei sensori dell’edificio, l’username deve essere l'admin dell’edificio
    // Collections:
    //  Sensors: aggiungi il sensore
    //  Buildings: aggiungi il sensore alla lista dei sensori dell’edificio
    // Risposta:
    //  String: messaggio di conferma
    public String addSensorToBuilding(AddSensorToBuildingRequest addSensorToBuildingRequest) {

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
            Document notFoundBuilding = new Document("error", "Building does not exist or user is not admin");
            return notFoundBuilding.toJson();
        }

        // Controlla che il sensore esista e che sia nel building
        Bson sensorFilter = elemMatch("sensors", Filters.eq("id", sensorId));
        Document foundSensor = collection.find(sensorFilter).first();
        if (foundSensor == null) {
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
        Document removedSensor = new Document("id", sensorId);
        return removedSensor.toJson();

    }

    // Descrizione:
    //  Inserisce una lettura del sensore, l’username deve essere l'admin dell’edificio
    // Collections:
    //  Buildings: controlla che l'edificio esista, che l'admin sia admin
    //  Sensors: controlla che il sensore sia nell'edificio e prende il tipo
    //  Readings: inserisce la lettura
    // Risposta:
    //  String: messaggio di conferma
    public String addReading(AddReadingRequest request) {
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
            Document notFoundSensor = new Document("error", "Sensor type not supported");
            return notFoundSensor.toJson();
        }
        readingsCollection.insertOne(readingDocument);
        jedis.set(redisKey, redisValue.toJson());

        return readingDocument.toJson();
    }

    // Descrizione:
    //  Ottiene la lista dei sensori degli edifici dell'utente 
    // Collections:
    //  Buildings: ottiene la lista dei sensori degli edifici dell'utente 
    //  Readings: se serve ottiene le ultime letture dei sensori
    // Risposta:
    //  String: lista dei sensori
    public String getUserSensors(String username, Integer buildingID) {
        if (username == null || buildingID == null) {
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
            Document notFoundBuilding = new Document("error", "Building does not exist or user is not in building");
            return notFoundBuilding.toJson();
        }

        // Crea lista dei sensori
        List<Document> sensors = foundBuilding.getList("sensors", Document.class);

        // Per ogni sensore ottieni l'ultima lettura
        JSONArray result = new JSONArray();
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

    // Descrizione:
    //   Calcola la statistica rainy days
    public Document getRainyDays(int buildingId, int yearNumber, int monthNumber) {
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

        if(result.first() != null)
            return result.first();

        Document ret = new Document();
        ret.append("_id", "");
        ret.append("count", 0);
        return ret;
    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getRainyDaysCache(Integer buildingId, Integer yearNumber, Integer monthNumber) {
        
        // Controlla se c'e' nel KV DB
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":rainyDays";
        if (jedis.exists(redisKey)) {
            System.out.println("Rainy days found in Redis");
            return jedis.get(redisKey);
        }
        
        // Altrimenti ritorna null (MISS)
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per i giorni di pioggia
    public void setRainyDaysCache(Integer buildingId, Integer yearNumber, Integer monthNumber, String value) {

        System.out.println("Setting rainy days in Redis");
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":rainyDays";
        jedis.set(redisKey, value);

    }

    // Descrizione:
    //   Calcola la statistica top 5 power consumption
    public JSONArray getTop5PowerConsumption(int buildingId, int yearNumber, int monthNumber, int dayNumber) {
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

        return ret;
    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getTop5PowerConsumptionCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber) {
        
        // Controlla se c'e' nel KV DB
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":top5PowerConsumption";
        if (jedis.exists(redisKey)) {
            System.out.println("Top 5 Power Consumption found in Redis");
            return jedis.get(redisKey);
        }
        
        // Altrimenti ritorna null (MISS)
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setTop5PowerConsumptionCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber, String value) {

        System.out.println("Setting Top 5 Power Consumption in Redis");
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":top5PowerConsumption";
        jedis.set(redisKey, value);

    }

    // Descrizione
    //   Calcola la statistica peak temperature
    public JSONArray getPeakTemperature(int buildingId, int yearNumber, int monthNumber, int dayNumber) {
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

        return ret;
    }

    // Descrizione
    //  Controlla se la statistica e' nel KV DB
    public String getPeakTemperatureCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber) {
        
        // Controlla se c'e' nel KV DB
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":peakTemperature";
        if (jedis.exists(redisKey)) {
            System.out.println("Peak Temperature found in Redis");
            return jedis.get(redisKey);
        }
        
        // Altrimenti ritorna null (MISS)
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setPeakTemperatureCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber, String value) {

        System.out.println("Setting Peak Temperature in Redis");
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":peakTemperature";
        jedis.set(redisKey, value);

    }

    // Descrizione:
    //   Calcola la statistica peak power consumption
    public JSONArray getPeakPowerConsumptionHours(int buildingId, int yearNumber, int monthNumber, int dayNumber) {
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

        return ret;
    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getPeakPowerConsumptionHoursCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber) {
        
        // Controlla se c'e' nel KV DB
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":peakPowerConsumption";
        if (jedis.exists(redisKey)) {
            System.out.println("Peak Power Consumption found in Redis");
            return jedis.get(redisKey);
        }
        
        // Altrimenti ritorna null (MISS)
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setPeakPowerConsumptionHoursCache(Integer buildingId, Integer yearNumber, Integer monthNumber, Integer dayNumber, String value) {

        System.out.println("Setting Peak Power Consumption in Redis");
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":" + dayNumber + ":peakPowerConsumption";
        jedis.set(redisKey, value);

    }

    // Descrizione:
    //   Calcola la statistica percentage of power from solar panels
    public Document getPercentageOfPowerFromSolarPanels(int buildingId, int yearNumber, int monthNumber) {
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
        stage2.append("_id", "$timestamp");
        stage2.append("totalPowerConsumption", new Document("$sum", "$consumption"));
        stage2.append("totalPowerProduction", new Document("$sum", "$production"));
        stage2 = new Document("$group", stage2);


        Document stage3 = new Document();
        stage3.append("percent", new Document("$divide", List.of("$totalPowerProduction", "$totalPowerConsumption")));
        stage3 = new Document("$addFields", stage3);

        Document stage4 = new Document();
        stage4.append("_id", "");
        stage4.append("avgPercent", new Document("$avg", "$percent"));
        stage4 = new Document("$group", stage4);

        AggregateIterable<Document> result = readingsCollection.aggregate(
            Arrays.asList(
                Aggregates.match(stage1),
                stage2,
                stage3,
                stage4
            )
        );

        if(result.first() != null) {
            return new Document("percentage", 
                100 * result.first().getDouble("avgPercent")
            );
        }

        Document ret = new Document("percentage", 0);
        return ret;

    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getPercentageOfPowerFromSolarPanelsCache(Integer buildingId, Integer yearNumber, Integer monthNumber) {
        
        // Controlla se c'e' nel KV DB
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":percentageOfPowerFromSolarPanels";
        if (jedis.exists(redisKey)) {
            System.out.println("Percentage of Power from Solar Panels found in Redis");
            return jedis.get(redisKey);
        }
        
        // Altrimenti ritorna null (MISS)
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setPercentageOfPowerFromSolarPanelsCache(Integer buildingId, Integer yearNumber, Integer monthNumber, String value) {

        System.out.println("Setting Percentage of Power from Solar Panels in Redis");
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":percentageOfPowerFromSolarPanels";
        jedis.set(redisKey, value);

    }

    // Descrizione:
    //   Calcola la statistica most humid day
    public Document getMostHumidDay(Integer buildingId, Integer yearNumber, Integer monthNumber) {

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

        if(result.first() != null)
            return result.first();

        Document ret = new Document();
        ret.append("_id", "");
        ret.append("count", 0);
        return ret;

    }

    // Descrizione:
    //   Controlla se la statistica e' nel KV DB
    public String getMostHumidDayCache(Integer buildingId, Integer yearNumber, Integer monthNumber) {
        
        // Controlla se c'e' nel KV DB
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":mostHumidDay";
        if (jedis.exists(redisKey)) {
            System.out.println("Most Humid Day found in Redis");
            return jedis.get(redisKey);
        }
        
        // Altrimenti ritorna null (MISS)
        return null;
    }

    // Descrizione:
    //   Setta il KV DB per la statistica
    public void setMostHumidDayCache(Integer buildingId, Integer yearNumber, Integer monthNumber, String value) {

        System.out.println("Setting Most Humid Day in Redis");
        String redisKey = "statistics:" + buildingId + ":" + yearNumber + ":" + monthNumber + ":mostHumidDay";
        jedis.set(redisKey, value);

    }

}