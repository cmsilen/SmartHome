package it.unipi.SmartHome.service;

import com.mongodb.client.model.Aggregates;
import it.unipi.SmartHome.model.AddReadingRequest;
import it.unipi.SmartHome.model.AddSensorToBuildingRequest;
import it.unipi.SmartHome.model.Building;
import it.unipi.SmartHome.model.User;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

import javax.print.Doc;

import com.mongodb.client.*;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.Document;
import java.lang.reflect.Method;
import static com.mongodb.client.model.Updates.pull;
import static com.mongodb.client.model.Updates.push;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Accumulators.*;
import static com.mongodb.client.model.Sorts.*;

@Service
public class UserService {

    // NOTA si potrebbero usare delle utility function tipo utenteEsiste(username) oppure edificioEsiste(id)
    // TODO aggiungere KV in GET /sensors
    // TODO aggiornare KV in POST /reading 
    // TODO aggiornare POST /reading il timestamp e' sbagliato
    // TODO aggiungere password ad utente


    // Parametri di connessione a MongoDB
    String usersCollectionName = "Users";
    String buildingsCollection = "Buildings";
    String sensorsCollectionName = "Sensors";
    String readingsCollectionName = "Readings";
    String dbName = "SmartHome";

    // Connessione a MongoDB
    ConnectionString uri = new ConnectionString("mongodb://localhost:27017");
    MongoClient mongoClient = MongoClients.create(uri);
    MongoDatabase database = mongoClient.getDatabase(dbName);
    // Secondo me quetsa roba e' una maialata pero' per ora teniamola cosi'


    // Descrizione:
    //   Registrazione di un nuovo utente
    // Collections:
    //   Users: Inserisci nuovo utente
    // Risposta:
    //   String: messaggio di conferma
    public String signUpUser(User user) {

        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(usersCollectionName);

        // Controllo che non ci sia gia' un utente con lo stesso username
        Document foundUser = collection.find(Filters.eq("username", user.getUsername())).first();
        if (foundUser != null) {
            return "User already exists";
        }
        // NOTA indice su username        

        // Inserisco il nuovo utente
        Document userDocument = new Document("username", user.getUsername())
            .append("name", user.getName())
            .append("surname", user.getSurname())
            .append("password", user.getPassword())
            .append("buildings", new org.bson.types.BasicBSONList());
        collection.insertOne(userDocument);
        return "User created";

    }

    // Descrizione:
    //   Effettua il login dell’utente
    // Collections:
    //   Users: Controlla se l'utente esiste e se la password e' corretta
    // Risposta:
    //   String: messaggio di conferma
    public String loginUser(String username, String password) {

        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(usersCollectionName);

        // Controllo che username e password siano corretti
        Document foundUser = collection.find(Filters.and(
            Filters.eq("username", username),
            Filters.eq("password", password)
        )).first();

        // Se l'utente esiste e la password e' corretta ritorno un messaggio di conferma altrimenti di errore
        if (foundUser != null) {
            return "User logged in successfully!";
        } else {
            return "Invalid username or password";
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
        
        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> usersCollection = database.getCollection(usersCollectionName);

        // Controllo che non ci sia gia' un edificio con lo stesso nome
        Document foundBuilding = collection.find(
            Filters.eq("id", building.getId())
        ).first();
        if (foundBuilding != null) {
            return "Building already exists";
        }

        // Controllo che l'utente esista
        Bson filter = Filters.eq("username", building.getAdmin());
        Document foundUser = usersCollection.find(filter).first();
        if (foundUser == null) {
            return "Admin not found";
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

        return "Building created";

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
            return "Building not found";
        }

        // Controllo che l'utente sia l'admin dell'edificio
        String admin = foundBuilding.getString("admin");
        if (!admin.equals(username)) {
            return "User is not admin of the building";
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
        return "Building deleted successfully! id: " + id;
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
            return "Building not found";
        }

        // Controllo che gli utenti esistano
        Document foundUser = usersCollection.find(
            Filters.eq("username", username)
        ).first();
        if (foundUser == null) {
            return "User not found";
        }
        // Controllo che l'admin sia effettivamente admin
        if (admin.equals(foundBuilding.getString("admin")) == false) {
            return "User is not admin of the building";
        }

        // Controllo che l'utente non appartenga già all'edificio
        Bson buildingFilter = elemMatch("buildings", Filters.eq("buildingID", id));
        Bson userFilter = Filters.eq("username", username);
        Document foundUserInBuilding = usersCollection.find(
            Filters.and(buildingFilter, userFilter)
        ).first();
        if (foundUserInBuilding != null) {
            return "User already in building";
        }

        // Aggiungo l'edificio alla lista dell'utente
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

        return "User added to building successfully!";
    }

    // Descrizione:
    //   Ottiene la lista degli edifici dell'utente
    // Collections:
    //   Users: Ottiene la lista degli edifici dell'utente
    // Risposta:
    //   Lista degli edifici dell'utente
    public String getUserBuildings(String username) {
    

        // Ottieni la Collection
        MongoCollection<Document> usersCollection = database.getCollection(usersCollectionName);

        // Controlla che l'utente esista
        Bson filter = Filters.eq("username", username);
        Document foundUser = usersCollection.find(filter).first();
        if (foundUser == null) {
            return "User not found";
        }

        // Leggi e concatena gli edifici
        String response = "";
        List<Document> buildings = foundUser.getList("buildings", Document.class);
        for (Document building : buildings) {
            String name = building.getString("buildingName");
            Integer id = building.getInteger("buildingID");
            response = response + " " + "name: " + name + ", id: " + id + "\n"; 
        }
        return response;

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
        Integer sensorId = addSensorToBuildingRequest.getSensor().getId();
        String sensorName = addSensorToBuildingRequest.getSensor().getName();
        String sensorType = addSensorToBuildingRequest.getSensor().getType();
        Integer buildingId = addSensorToBuildingRequest.getSensor().getBuildingId();
        
        // Controlla che l'id del sensore sia unico
        Bson filter = Filters.eq("id", sensorId);
        Document foundSensor = sensorsCollection.find(filter).first();
        if (foundSensor != null) {
            return "Sensor already exists";
        }
        
        // Controlla che l'edificio esista e che l'admin sia admin
        Bson buildingFilter = Filters.eq("id", buildingId);
        Bson adminFilter = Filters.eq("admin", username);
        filter = Filters.and(buildingFilter, adminFilter);
        Document foundBuilding = collection.find(filter).first();
        if (foundBuilding == null) {
            return "Building does not exist or user is not admin";
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

        return "Sensor added";
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
            return "Building does not exist or user is not admin";
        }

        // Controlla che il sensore esista e che sia nel building
        Bson sensorFilter = elemMatch("sensors", Filters.eq("id", sensorId));
        Document foundSensor = collection.find(sensorFilter).first();
        if (foundSensor == null) {
            return "Sensor does not exist or is not in building";
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
        return "Sensor removed";

    }

    // Descrizione:
    //  Inserisce una lettura del sensore, l’username deve essere l'admin dell’edificio
    // Collections:
    //  Buildings: controlla che l'edificio esista, che l'admin sia admin e che il sensore sia nell'edificio
    //  Readings: inserisce la lettura
    // Risposta:
    //  String: messaggio di conferma
    public String addReading(AddReadingRequest request) {
    
        // Accedi alle collections
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        // Estrai informazioni
        Integer sensorId = request.getSensorId();
        Integer buildingId = request.getBuildingId();
        String username = request.getUsername();
        Long timestamp = request.getTimestamp();
        Float value1 = request.getValue1();
        Float value2 = request.getValue2();
        String type = request.getType();

        // Controlla che l'edificio esista, che l'admin sia admin e che il sensore sia nell'edificio
        // NOTA metti un indice per velocizzare questa cosa
        Bson buildingFilter = Filters.eq("id", buildingId);
        Bson adminFilter = Filters.eq("admin", username);
        Bson sensorFilter = elemMatch("sensors", Filters.eq("id", sensorId));
        Bson filter = Filters.and(buildingFilter, adminFilter, sensorFilter);
        Document foundBuilding = collection.find(filter).first();
        if (foundBuilding == null) {
            return "Building does not exist or user is not admin or sensor is not in building";
        }

        // Aggiungi la lettura alla collection Readings, uso il tipo di sensore per decidere se aggiungere 
        // value1 e/o value2 e come chiamare i campi che li contengon
        Date date = new Date(timestamp);
        Document readingDocument = new Document("sensorID", sensorId)
            .append("buildingID", buildingId)
            .append("timestamp", date);

        if (type.equals("PowerConsumption")) {
            readingDocument.append("consumption", value1);
        } 
        else if (type.equals("SolarPanel")) {
            readingDocument.append("production", value1);
        } 
        else if (type.equals("Temperature")) {
            readingDocument.append("temperature", value1);
        }
        else if (type.equals("Humidity")) {
            readingDocument.append("apparentTemperature", value1);
            readingDocument.append("humidity", value2);
        } 
        else if (type.equals("Pressure")) {
            readingDocument.append("pressure", value1);
        } 
        else if (type.equals("Wind")) {
            readingDocument.append("windSpeed", value1);
            readingDocument.append("windBearing", value2);
        } 
        else if (type.equals("Precipitation")) {
            readingDocument.append("precipitationIntensity", value1);
            readingDocument.append("precipitationProbability", value2);
        } 
        else {
            return "Sensor type not supported";
        }
        readingsCollection.insertOne(readingDocument);

        return "Reading added successfully!";

    }

    // Descrizione:
    //  Ottiene la lista dei sensori degli edifici dell'utente 
    // Collections:
    //  Buildings: ottiene la lista dei sensori degli edifici dell'utente 
    //  Readings: se serve ottiene le ultime letture dei sensori
    // Risposta:
    //  String: lista dei sensori
    public String getUserSensors(String username) {
    
        // Ottieni la Collection
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> readingsCollection = database.getCollection(readingsCollectionName);

        // Ottieni la lista dei sensori dell'utente
        Document buildingFilter = new Document("users", new Document("$in", List.of(username)));
        List<Document> buildings = collection.find(buildingFilter).into(new ArrayList<>());
        if (buildings.isEmpty()) {
            return "User has no buildings";
        }
        List<Integer> sensorIds = new ArrayList<>();
        for (Document building : buildings) {
            List<Document> sensors = building.getList("sensors", Document.class);
            for (Document sensor : sensors) {
                Integer sensorId = sensor.getInteger("id");
                sensorIds.add(sensorId);
            }
        }

        // Per ogni sensore ottieni l'ultima lettura
        List<String> sensorLastreadings = new ArrayList<>();
        for (Integer sensorId : sensorIds) {

            // Controlla se c'e' nel KV DB 

            // Altrimenti la va a prendere da MongoDB
            Bson filter = Filters.eq("sensorID", sensorId);
            Document foundReading = readingsCollection.find(filter).sort(new Document("timestamp", -1)).first();
            if (foundReading.containsKey("consumption")) {
                sensorLastreadings.add("Power Consumption :: consumption: " + foundReading.getDouble("consumption"));
            } 
            else if (foundReading.containsKey("production")) {
                sensorLastreadings.add("Solar Panel :: production: " + foundReading.getDouble("production"));
            } 
            else if (foundReading.containsKey("temperature")) {
                sensorLastreadings.add("Temperature :: temperature: " + foundReading.getDouble("temperature"));
            } 
            else if (foundReading.containsKey("apparentTemperature")) {
                sensorLastreadings.add("Humidity :: apparentTemperature: " + foundReading.getDouble("apparentTemperature") + ", humidity: " + foundReading.getDouble("humidity"));
            } 
            else if (foundReading.containsKey("pressure")) {
                sensorLastreadings.add("Pressure :: pressure: " + foundReading.getDouble("pressure"));
            } 
            else if (foundReading.containsKey("windSpeed")) {
                sensorLastreadings.add("Wind :: windSpeed: " + foundReading.getDouble("windSpeed") + ", windBearing: " + foundReading.getDouble("windBearing"));
            } 
            else if (foundReading.containsKey("precipitationIntensity")) {
                sensorLastreadings.add("Precipitation :: precipitationIntensity: " + foundReading.getDouble("precipitationIntensity") + ", SolarPanelprecipitationProbability: " + foundReading.getDouble("precipitationProbability"));
            }
            else {
                sensorLastreadings.add("Sensor type not supported");
            }
        }

        // Converti Liste in risposta
        String response = "";
        Iterator<String> readingIterator = sensorLastreadings.iterator();
        Iterator<Integer> idIterator = sensorIds.iterator();
        while (readingIterator.hasNext() && idIterator.hasNext()) {
            String reading = readingIterator.next();
            Integer id = idIterator.next();
            response += "Sensor id: " + id + ", last reading: " + reading + "\n";
        }

        return response;
    }

    // Descrizione:
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

    public Document percentageOfPowerFromSolarPanels(int buildingId, int yearNumber, int monthNumber) {
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
        // stage1.append("$or", new Document()
        //     .append("consumption", new Document("$exists", true))
        //     .append("production", new Document("$exists", true))
        // );

        // Proietta il giorno e l'umidità
        Document stage2 = new Document();
        stage2.append("consumption", 1);
        stage2.append("production", 1);
        stage2.append("date", new Document("$dateToString", new Document("format", "%d-%m-%Y").append("date", "$timestamp")));

        // Raggruppa per giorno e calcola l'umidità massima
        Document stage3 = new Document();
        stage3.append("_id", null);
        stage3.append("sumConsumption", new Document("$sum", "$consumption"));
        stage3.append("sumProduction", new Document("$sum", "$production"));
        stage3 = new Document("$group", stage3);

        AggregateIterable<Document> result = readingsCollection.aggregate(
            Arrays.asList(
                Aggregates.match(stage1),
                Aggregates.project(stage2),
                stage3
            )
        );

        if(result.first() != null) {
            return new Document("percentage", 
                100 * result.first().getDouble("sumProduction") / result.first().getDouble("sumConsumption")
            );
        }

        Document ret = new Document();
        return ret;

    }

    public Document mostHumidDay(Integer buildingId, Integer yearNumber, Integer monthNumber) {

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

        // Proietta il giorno e l'umidità
        Document stage2 = new Document();
        stage2.append("humidity", 1);
        stage2.append("date", new Document("$dateToString", new Document("format", "%d-%m-%Y").append("date", "$timestamp")));

        // Raggruppa per giorno e calcola l'umidità massima
        Document stage3 = new Document();
        stage3.append("_id", "$date");
        stage3.append("maxHumidity", new Document("$max", "$humidity"));
        stage3 = new Document("$group", stage3);

        Document stage4 = new Document();
        stage4.append("_id", null);
        stage4.append("maxHumidity", new Document("$max", "$maxHumidity"));
        stage4 = new Document("$group", stage4);

        AggregateIterable<Document> result = readingsCollection.aggregate(
                Arrays.asList(
                        Aggregates.match(stage1),
                        Aggregates.project(stage2),
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

}