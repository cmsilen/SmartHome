import requests

if __name__ == "__main__":

    # Dati della Richiesta da fare
    url = "http://localhost:8080/signup"
    data = {
        "username": "Sandro",
        "name": "Simone",
        "surname": "Gallo",
        "password": "pwd"
    }

    # Parso la risposta
    response = requests.post(url, json=data)
    response_data = response.json()

    # Controllo che sia giusta
    passed = True
    for key in data.keys():
        if key not in response_data or response_data[key] != data[key]:
            passed = False
            break 

    # Stampo a video il risultato
    if passed:
        print("Passed: Signup 1 effettuato")
    else:
        print("Not Passed: Signup 1 non effettuato")

    # Dati della Richiesta da fare
    url = "http://localhost:8080/signup"
    data = {
        "username": "Wario",
        "name": "Simone",
        "surname": "Gallo",
        "password": "pwd"
    }

    # Parso la risposta
    response = requests.post(url, json=data)
    response_data = response.json()

    # Controllo che sia giusta
    passed = True
    for key in data.keys():
        if key not in response_data or response_data[key] != data[key]:
            passed = False
            break 

    # Stampo a video il risultato
    if passed:
        print("Passed: Signup 2 effettuato")
    else:
        print("Not Passed: Signup 2 non effettuato")

