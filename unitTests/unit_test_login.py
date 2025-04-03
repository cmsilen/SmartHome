import requests

if __name__ == "__main__":

    # Dati della Richiesta da fare
    url = "http://localhost:8080/login"
    data = {
        "username": "Sandro",
        "password": "pwd"
    }

    # Parso la risposta
    response = requests.post(url, json=data)
    response_data = response.json()
    expected_data = {
        "username": "Sandro",
        "name": "Simone",
        "surname": "Gallo",
        "password": "pwd"
    }

    # Controllo che sia giusta
    passed = True
    for key in expected_data.keys():
        if key not in response_data or response_data[key] != expected_data[key]:
            passed = False
            break 

    # Stampo a video il risultato
    if passed:
        print("Passed: Login effettuato")
    else:
        print("Not Passed: Login non effettuato")

