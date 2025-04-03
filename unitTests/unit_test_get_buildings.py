import requests

if __name__ == "__main__":

    # Dati della Richiesta da fare
    url = "http://localhost:8080/buildings?username=Sandro"

    # Parso la risposta
    response = requests.get(url)
    response_data = response.json()
    expected_data = {
        "buildings": [
            {"name": "SandroHouse", "id": 0}
        ]
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
        print("Passed: get buildings effettuata")
    else:
        print("Not Passed: get buildings non effettuata")
