import sys
import os
import time
import requests
import json


if __name__ == "__main__":

    # Leggi le letture da inviare da un file JSON 
    with open('readings.json', 'r') as file:
        readings = json.load(file)

    # Aspetta un minuti e poi invia le letture ciclicamente
    url = "http://localhost:8080/reading"
    while True:
        time.sleep(10)  
        print("Sending readings...")
        for reading in readings:
            response = requests.post(url, json=reading)
        print("Readings sent.")


