import requests

if __name__ == "__main__":

    # Dati della Richiesta da fare
    url = "http://localhost:8080/reading"
    data = {
        "sensorId": 0,
        "buildingId": 0,
        "timestamp": 0,
        "username": "Sandro",
        "value1": 0,
        "value2": 0
    }

    # Parso la risposta
    response = requests.post(url, json=data)
    response_data = response.json()
    expected_data = {
        "sensorID": 0
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
        print("Passed: Aggiunta Lettura effettuata")
    else:
        print("Not Passed: Aggiunta Lettura non effettuata")

