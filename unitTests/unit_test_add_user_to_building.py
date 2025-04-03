import requests

if __name__ == "__main__":

    # Dati della Richiesta da fare
    url = "http://localhost:8080/building/user"
    data = {
        "username": "Wario",
        "admin": "Sandro",
        "buildingId": 0
    }

    # Parso la risposta
    response = requests.post(url, json=data)
    response_data = response.json()
    expected_data = {
        "username": "Wario",
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
        print("Passed: Aggiunta Utente ad Edificio effettuata")
    else:
        print("Not Passed: Aggiunta Utente ad Edificio non effettuata")

