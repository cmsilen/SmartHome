import requests

if __name__ == "__main__":

    # Dati della Richiesta da fare
    data = {
        "id": 1,
        "admin": "Sandro"
    }
    url = f"http://localhost:8080/building?id={data["id"]}&admin={data["admin"]}"

    # Parso la risposta
    response = requests.delete(url, json=data)
    response_data = response.json()
    expected_data = {
        "id": 1,
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
        print("Passed: Building eliminato")
    else:
        print("Not Passed: Building eliminato")
