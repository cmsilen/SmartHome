package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

import com.mongodb.client.*;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.Document;

@Service
public class UserService {

    // Connessione a MongoDB
    ConnectionString uri = new ConnectionString("mongodb://localhost:27017");
    MongoClient mongoClient = MongoClients.create(uri);
    MongoDatabase database = mongoClient.getDatabase("mydb");
    // Secondo me quetsa roba e' una maialata pero' per ora teniamola cosi'


    // Registrazione di un nuovo utente
    public String signUpUser(User user) {

        MongoCollection<Document> collection = database.getCollection("users");

        Document foundUser = collection.find(Filters.eq("username", user.getUsername())).first();
        if (foundUser != null) {
            return "User already exists";
        }

        Document userDocument = new Document("username", user.getUsername())
            .append("name", user.getName())
            .append("surname", user.getSurname())
            .append("password", user.getPassword());
        collection.insertOne(userDocument);
        return "User created";

    }

    public String loginUser(String username, String password) {

        MongoCollection<Document> collection = database.getCollection("users");

        Document foundUser = collection.find(Filters.and(
            Filters.eq("username", username),
            Filters.eq("password", password)
        )).first();

        if (foundUser != null) {
            return "User logged in successfully!";
        } else {
            return "Invalid username or password";
        }
    }

}