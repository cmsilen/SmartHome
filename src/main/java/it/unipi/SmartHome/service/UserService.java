package it.unipi.SmartHome.service;

import it.unipi.SmartHome.model.AddSensorToBuildingRequest;
import it.unipi.SmartHome.model.Building;
import it.unipi.SmartHome.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Filter;

import com.mongodb.client.*;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.Document;
import static com.mongodb.client.model.Updates.pull;
import static com.mongodb.client.model.Updates.push;
import static com.mongodb.client.model.Filters.elemMatch;
@Service
public class UserService {

    // NOTA si potrebbero usare delle utility function tipo utenteEsiste(username) oppure edificioEsiste(id)
    // NOTA finire deleteBuilding

    // Parametri di connessione a MongoDB
    String usersCollectionName = "Users";
    String buildingsCollection = "Buildings";
    String sensorsCollectionName = "Sensors";
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
            .append("location", building.getLocation())
            .append("users", Arrays.asList(building.getAdmin()))
            .append("admin", building.getAdmin())
            .append("id", building.getId());
        collection.insertOne(buildingDocument);
        
        // Aggiorno la collection degli utenti inserendo l'edificio
        Document newBuilding = new Document("name", building.getName())
            .append("id", building.getId());
        Bson update = push("buildings", newBuilding);
        usersCollection.updateOne(filter, update);

        return "Building created";

    }

    // Descrizione:
    //   Rimuove un edificio
    // Collections:
    //   Users: Rimuovi edificio dalla lista degli edifici dell'utente
    //   Buildings: Rimuovi edificio
    // Risposta:
    //   String: messaggio di conferma
    public String removeBuilding(Integer id, String username) {
        
        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(buildingsCollection);
        MongoCollection<Document> usersCollection = database.getCollection(usersCollectionName);
        MongoCollection<Document> sensorsCollection = database.getCollection(sensorsCollectionName);

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
        Bson update = pull("buildings", new Document("id", id));
        Bson filter = elemMatch("buildings", Filters.eq("id", id));
        usersCollection.updateMany(filter, update);
        // NOTA indice su buildingId

        // Cancello i sensori dell'edificio 
        filter = Filters.eq("buildingId", id);
        sensorsCollection.deleteMany(filter);
        
        // Cancello le letture dei sensori dell'edificio

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
        Bson buildingFilter = elemMatch("buildings", Filters.eq("id", id));
        Bson userFilter = Filters.eq("username", username);
        Document foundUserInBuilding = usersCollection.find(
            Filters.and(buildingFilter, userFilter)
        ).first();
        if (foundUserInBuilding != null) {
            return "User already in building";
        }

        // Aggiungo l'edificio alla lista dell'utente
        String name = foundBuilding.getString("name");
        Document newBuilding = new Document("name", name)
            .append("id", id);
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
            String name = building.getString("name");
            Integer id = building.getInteger("id");
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
            .append("buildingId", buildingId);
        sensorsCollection.insertOne(sensorDocument);

        // Aggiungi sensore alla lista di sensori dell'edificio
        Document embeddedSensorDocument = new Document("name", sensorName)
            .append("type", sensorType)
            .append("id", sensorId);
        Bson update = push("sensors", embeddedSensorDocument);
        collection.updateOne(filter, update);

        return "Sensor added";
    }

}