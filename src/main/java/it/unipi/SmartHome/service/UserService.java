package it.unipi.SmartHome.service;

import it.unipi.SmartHome.model.Building;
import it.unipi.SmartHome.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;

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


    String usersCollectionName = "Users";
    String buildingsCollection = "Buildings";
    String dbName = "SmartHome";

    // Connessione a MongoDB
    ConnectionString uri = new ConnectionString("mongodb://localhost:27017");
    MongoClient mongoClient = MongoClients.create(uri);
    MongoDatabase database = mongoClient.getDatabase(dbName);
    // Secondo me quetsa roba e' una maialata pero' per ora teniamola cosi'


    // Registrazione di un nuovo utente
    public String signUpUser(User user) {

        // Accedo alla Collection
        MongoCollection<Document> collection = database.getCollection(usersCollectionName);

        // Controllo che non ci sia gia' un utente con lo stesso username
        Document foundUser = collection.find(Filters.eq("username", user.getUsername())).first();
        if (foundUser != null) {
            return "User already exists";
        }

        // Inserisco il nuovo utente
        Document userDocument = new Document("username", user.getUsername())
            .append("name", user.getName())
            .append("surname", user.getSurname())
            .append("password", user.getPassword())
            .append("buildings", new org.bson.types.BasicBSONList());
        collection.insertOne(userDocument);
        return "User created";

    }

    // Effettua il login dell’utente controllando che username e password siano corretti
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

    // Aggiunge un nuovo edificio 
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

    public String removeBuilding(Integer id, String username) {
        
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

        // Elimino l'edificio
        collection.deleteOne(Filters.eq("id", id));
        return "Building deleted successfully! id: " + id;
    }

}