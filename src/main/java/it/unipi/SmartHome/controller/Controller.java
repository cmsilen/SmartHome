package it.unipi.SmartHome.controller;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.processing.Generated;

import it.unipi.SmartHome.model.*;
import it.unipi.SmartHome.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

// import com.example.demo.service.UserService;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;


@RestController
public class Controller {

    @Autowired
    private UserService userService;
    // private UserService userService = new UserService();

    // Descrizione:
    //  Registra l’utente controllando che non vi sia gia' un utente con lo stesso username
    // Parametri:
    //  String: username 
    //  String: name
    //  String: surname
    //  String: password
    // Risposta:
    //  String: messaggio di conferma
    @PostMapping("/signup")
    public String signup(@RequestBody User user) {

        String response = userService.signUpUser(user);
        return response;

    }

    // Descrizione:
    //  Effettua il login dell’utente controllando che username e password siano corretti
    //  (in teoria dovrebbe anche salvare i dati di login nella sessione)
    // Parametri:
    //  String: username
    //  String: password
    // Risposta:
    //  String: messaggio di conferma
    @PostMapping("/login")
    public String login(@RequestBody LoginRequest loginRequest) {

        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        String response = userService.loginUser(
            username,
            password
        );
        return response;

    }

    // Descrizione:
    //  Effettua il logout dell’utente (in teoria dovrebbe anche eliminare i dati di login dalla sessione)
    // Parametri:
    // 
    // Risposta:
    //  String: messaggio di conferma
    @GetMapping("/logout")
    public String logout() {

        return "User logged out successfully!";

    }
    
    // Descrizione:
    //  Restituisce la lista degli edifici dell’utente controllando che l’utente esista
    //  (passare il nome utente come parametro serve a evitare di salvare i dati di login nella sessione)
    // Parametri:
    //  String: username 
    // Risposta:
    //  buildingsResponse: lista degli edifici dell’utente
    @GetMapping("/buildings")
    public ResponseEntity<BuildingsResponse> getBuildings(@RequestParam(value = "username", defaultValue = "") String username) {

        BuildingsResponse response = new BuildingsResponse();
        return ResponseEntity.ok(response);
    }

    // Descrizione:
    //  Aggiunge un edificio alla lista degli edifici, il campo admin dell’edificio deve essere inteso
    //  come l’username dell’utente che e' loggato e che dunque sara' l’amministratore dell’edificio
    // Parametri:
    //  String: name
    //  String: location
    //  String[]: users
    //  String: admin
    // Risposta:
    //  String: messaggio di conferma
    @PostMapping("/building")
    public String addBuilding(@RequestBody Building building) {

        return "Building added successfully! name: " + building.getName() + ", location: " + building.getLocation() + ", users: " + String.join(", ", building.getUsers()) + ", admin: " + building.getAdmin();

    }

    // Descrizione:
    //  Elimina un edificio se l'utente e' l'admin 
    // Parametri:
    //  String: id
    //  String: username
    // Risposta:
    //  String: messaggio di conferma
    @DeleteMapping("/building")
    public String deleteBuilding(
        @RequestParam(value = "id", defaultValue = "") Integer id, 
        @RequestParam(value = "admin", defaultValue = "") String username
    ) {

        return "Building deleted successfully! id: " + id;

    }

    // Descrizione:
    //  Aggiunge un utente alla lista degli utenti dell’edificio, l’username dell’admin deve essere
    //  l'admin dell’edificio
    // Parametri:
    //  String: username
    //  String: admin
    //  Integer: buildingId
    // Risposta:
    //  String: messaggio di conferma
    @PostMapping("/building/user")
    public String addUserToBuilding(@RequestBody AddUserToBuildingRequest addUserToBuildingRequest) {

        return "User added to building successfully! username: " + addUserToBuildingRequest.getUsername() + ", admin: " + addUserToBuildingRequest.getAdmin() + ", buildingId: " + addUserToBuildingRequest.getBuildingId();

    }

    // Descrizione:
    //  Aggiunge un sensore alla lista dei sensori dell’edificio, l’username deve essere l'admin dell’edificio
    // Parametri:
    //  Sensor: sensor
    //  String: username
    // Risposta:
    //  String: messaggio di conferma
    @PostMapping("/building/sensor")
    public String addSensorToBuilding(@RequestBody AddSensorToBuildingRequest addSensorToBuildingRequest) {

        return "Sensor added to building successfully!";

    }

    // Descrizione:
    //  Elimina un sensore dalla lista dei sensori dell’edificio, l’username deve essere l'admin dell’edificio
    // Parametri:
    //  Integer: sensorId
    //  Integer: buildingId
    //  String: username
    // Risposta:
    //  String: messaggio di conferma
    @DeleteMapping("/building/sensor")
    public String deleteSensorFromBuilding(
        @RequestParam(value = "sensorId", defaultValue = "") Integer sensorId, 
        @RequestParam(value = "buildingId", defaultValue = "") Integer buildingId, 
        @RequestParam(value = "admin", defaultValue = "") String username
    ) {

        return "Sensor deleted from building successfully!";

    }

    // Descrizione:
    //  Restituisce la lista dei sensori con le ultime letture degli edifici dell'utente
    // Parametri:
    //  String: username
    // Risposta:
    //  Sensor[]: lista dei sensori
    @GetMapping("/sensors")
    public ResponseEntity<Sensor[]> getSensors(@RequestParam(value = "username", defaultValue = "") String username) {

        Sensor[] sensors = new Sensor[0];
        return ResponseEntity.ok(sensors);

    }

    // Descrizione:
    //  Restituisce il risultato della statistica richiesta
    // Parametri:
    //  Integer: statisticId
    // Risposta:
    //  String: risultato della statistica
    @GetMapping("/statistics")
    public String getStatistics(@RequestParam(value = "statisticId", defaultValue = "") Integer statisticId) {

        return "Statistics for sensorId";    
    
    }

}
