import requests

if __name__ == "__main__":

    # Dati della Richiesta da fare
    url = "http://localhost:8080/building"
    data = {
        "name": "SandroHouse",
        "id": 0,
        "location": "SandroLand",
        "users": [
            "string"
        ],
        "admin": "Sandro"
    }

    # Parso la risposta
    response = requests.post(url, json=data)
    response_data = response.json()
    expected_data = {
        "name": "SandroHouse",
        "id": 0,
        "location": "SandroLand",
        "users": [
            "Sandro"
        ],
        "admin": "Sandro"
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
        print("Passed: Building 1 creato")
    else:
        print("Not Passed: Building 1 non creato")

    # Parso la risposta
    response = requests.post(url, json=data)
    response_data = response.json()
    expected_data = {
        "name": "SandroHouse",
        "id": 1,
        "location": "SandroLand",
        "users": [
            "Sandro"
        ],
        "admin": "Sandro"
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
        print("Passed: Building 2 creato")
    else:
        print("Not Passed: Building 2 non creato")


