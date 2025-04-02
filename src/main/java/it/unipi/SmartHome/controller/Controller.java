package it.unipi.SmartHome.controller;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.processing.Generated;

import it.unipi.SmartHome.model.*;
import it.unipi.SmartHome.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

// import com.example.demo.service.UserService;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;


@RestController
public class Controller {

    // NOTA aggiungi POST reading
    // NOTA GET /buildings ritorna una stringa potrebbe anche ritornare un JSON

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
    public String getBuildings(@RequestParam(value = "username", defaultValue = "") String username) {

        String response = userService.getUserBuildings(username); 
        return response;
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

        String response = userService.addBuilding(building);
        return response;

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

        String response = userService.removeBuilding(id, username);
        return response;

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

        String response = userService.addUserToBuilding(
            addUserToBuildingRequest.getUsername(),
            addUserToBuildingRequest.getAdmin(),
            addUserToBuildingRequest.getBuildingId()
        );
        return response;

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

        String response = userService.addSensorToBuilding(addSensorToBuildingRequest);
        return response;

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

        String response = userService.removeSensorFromBuilding(sensorId, buildingId, username);
        return response;

    }

    // Descrizione:
    //  Inserisce una lettura del sensore, l’username deve essere l'admin dell’edificio
    // Parametri:
    //  Integer: sensorId
    //  Integer: buildingId
    //  String: username
    //  String: timestamp
    //  Float: value1
    //  Float: value2
    //  String: type
    // Risposta:
    //  String: messaggio di conferma
    @PostMapping("/reading")
    public String addReading(@RequestBody AddReadingRequest addReadingRequest) {
    
        String response = userService.addReading(addReadingRequest);
        return response;

    }

    // Descrizione:
    //  Restituisce la lista dei sensori con le ultime letture degli edifici dell'utente
    // Parametri:
    //  String: username
    // Risposta:
    //  Sensor[]: lista dei sensori
    @GetMapping("/sensors")
    public String getSensors(@RequestParam(value = "username", defaultValue = "") String username) {


        String response = userService.getUserSensors(username);
        return response;

    }

    // Descrizione:
    //   Number of rainy days given a month
    // Parametri:
    //   Integer: buildingId
    //   String: startTimestamp
    //   String: endTimestamp
    // Risposta:
    //  String: risultato della statistica
    @GetMapping("/statistics/rainydays")
    public String getRainyDays(
            @RequestParam(value = "buildingID", defaultValue = "") Integer buildingId,
            @RequestParam(value = "year", defaultValue = "") Integer year,
            @RequestParam(value = "month", defaultValue = "") Integer month
    ) {
        if(buildingId == null || year == null || month == null) {
            return "error";
        }

        // Controlla se e' nella cache
        String response = userService.getRainyDaysCache(buildingId, year, month);
        if (response != null) {
            return response;
        }

        // ha fatto MISS quindi aggiorna la cache
        response = userService.getRainyDays(buildingId, year, month).toJson();
        userService.setRainyDaysCache(buildingId, year, month, response);
        return response;

    }

    // Descrizione:
    //   Top 5 sensors with the highest power consumption
    // Parametri:
    //   Integer: buildingId
    //   String: startTimestamp
    //   String: endTimestamp
    // Risposta:
    //  String: risultato della statistica
    @GetMapping("/statistics/top5powerconsumption")
    public String getTop5PowerConsumption(
        @RequestParam(value = "buildingID", defaultValue = "") Integer buildingId,
        @RequestParam(value = "year", defaultValue = "") Integer year,
        @RequestParam(value = "month", defaultValue = "") Integer month,
        @RequestParam(value = "day", defaultValue = "") Integer day
    ) {
        if(buildingId == null || year == null || month == null || day == null) {
            return "error";
        }

        // Controlla se e' nella cache
        String response = userService.getTop5PowerConsumptionCache(buildingId, year, month, day);
        if (response != null) {
            return response;
        }

        // ha fatto MISS quindi aggiorna la cache
        response = userService.getTop5PowerConsumption(buildingId, year, month, day).toString(4);
        userService.setTop5PowerConsumptionCache(buildingId, year, month, day, response);
        return response;
    
    }

    // Descrizione:
    //   Time of peak temperature in the last day
    // Parametri:
    //   Integer: buildingId
    //   String: startTimestamp
    //   String: endTimestamp
    // Risposta:
    //  String: risultato della statistica
    @GetMapping("/statistics/peaktemperature")
    public String getPeakTemperature(
            @RequestParam(value = "buildingID", defaultValue = "") Integer buildingId,
            @RequestParam(value = "year", defaultValue = "") Integer year,
            @RequestParam(value = "month", defaultValue = "") Integer month,
            @RequestParam(value = "day", defaultValue = "") Integer day
    ) {
        if(buildingId == null || year == null || month == null || day == null) {
            return "error";
        }

        // Controlla se e' nella cache
        String response = userService.getPeakTemperatureCache(buildingId, year, month, day);
        if (response != null) {
            return response;
        }
        // ha fatto MISS quindi aggiorna la cache
        response = userService.getPeakTemperature(buildingId, year, month, day).toString(4);
        userService.setPeakTemperatureCache(buildingId, year, month, day, response);
        return response;
    }

    // Descrizione:
    //   Peak energy usage hours in a day
    // Parametri:
    //   Integer: buildingId
    //   String: startTimestamp
    //   String: endTimestamp
    // Risposta:
    //  String: risultato della statistica
    @GetMapping("/statistics/peakpowerconsumptionhours")
    public String getPeakPowerConsumptionHours(
            @RequestParam(value = "buildingID", defaultValue = "") Integer buildingId,
            @RequestParam(value = "year", defaultValue = "") Integer year,
            @RequestParam(value = "month", defaultValue = "") Integer month,
            @RequestParam(value = "day", defaultValue = "") Integer day
    ) {
        if(buildingId == null || year == null || month == null || day == null) {
            return "error";
        }

        // Controlla se e' nella cache
        String response = userService.getPeakPowerConsumptionHoursCache(buildingId, year, month, day);
        if (response != null) {
            return response;
        }

        // ha fatto MISS quindi aggiorna la cache
        response = userService.getPeakPowerConsumptionHours(buildingId, year, month, day).toString(4);
        userService.setPeakPowerConsumptionHoursCache(buildingId, year, month, day, response);
        return response;

    }

    // Descrizione:
    //   Percentage of power from solar panels
    // Parametri:
    //   Integer: buildingId
    //   String: startTimestamp
    //   String: endTimestamp
    // Risposta:
    //  String: risultato della statistica
    @GetMapping("/statistics/powerfromsolarpanels")
    public String getPercentageOfPowerFromSolarPanels(
            @RequestParam(value = "buildingID", defaultValue = "") Integer buildingId,
            @RequestParam(value = "year", defaultValue = "") Integer year,
            @RequestParam(value = "month", defaultValue = "") Integer month
    ) {
        if(buildingId == null || year == null || month == null) {
            return "error";
        }

        // Controlla se e' nella cache
        String response = userService.getPercentageOfPowerFromSolarPanelsCache(buildingId, year, month);
        if (response != null) {
            return response;
        }

        // ha fatto MISS quindi aggiorna la cache
        response = userService.getPercentageOfPowerFromSolarPanels(buildingId, year, month).toJson();
        userService.setPercentageOfPowerFromSolarPanelsCache(buildingId, year, month, response);
        return response;
        
    }

    // Descrizione:
    //   Most humid day in a given month
    // Parametri:
    //   Integer: buildingId
    //   String: startTimestamp
    //   String: endTimestamp
    // Risposta:
    //  String: risultato della statistica
    @GetMapping("/statistics/mosthumidday")
    public String getMostHumidDay(
            @RequestParam(value = "buildingID", defaultValue = "") Integer buildingId,
            @RequestParam(value = "year", defaultValue = "") Integer year,
            @RequestParam(value = "month", defaultValue = "") Integer month
    ) {
        if(buildingId == null || year == null || month == null) {
            return "error";
        }

        // Controlla se e' nella cache
        String response = userService.getMostHumidDayCache(buildingId, year, month);
        if (response != null) {
            return response;
        }
        
        // ha fatto MISS quindi aggiorna la cache
        response = userService.getMostHumidDay(buildingId, year, month).toJson();
        userService.setMostHumidDayCache(buildingId, year, month, response);
        return response;

    }

}
