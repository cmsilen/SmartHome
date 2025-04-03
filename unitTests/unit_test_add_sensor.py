import requests

if __name__ == "__main__":

    # Dati della Richiesta da fare
    url = "http://localhost:8080/building/sensor"
    data = {
        "sensor": {
            "id": 0,
            "name": "string",
            "type": "PowerConsumption",
            "buildingId": 0
        },
        "username": "Sandro"
    }

    # Parso la risposta
    response = requests.post(url, json=data)
    response_data = response.json()
    expected_data = {
        "id": 0,
        "name": "string",
        "type": "PowerConsumption",
        "buildingID": 0
    }

    # Controllo che sia giusta
    passed = True
    for key in expected_data.keys():
        if key not in response_data or response_data[key] != expected_data[key]:
            print(key)
            passed = False
            break 

    # Stampo a video il risultato
    print(response_data)
    if passed:
        print("Passed: Sensor 1 creato")
    else:
        print("Not Passed: Sensor 1 non creato")

    # Parso la risposta
    response = requests.post(url, json=data)
    response_data = response.json()
    expected_data = {
        "id": 1,
        "name": "string",
        "type": "PowerConsumption",
        "buildingID": 0
    }

    # Controllo che sia giusta
    passed = True
    for key in expected_data.keys():
        if key not in response_data or response_data[key] != expected_data[key]:
            passed = False
            break 

    # Stampo a video il risultato
    print(response_data)
    if passed:
        print("Passed: Sensor 2 creato")
    else:
        print("Not Passed: Sensor 2 non creato")

