import requests
import time

if __name__ == "__main__":

    # Dati della Richiesta da fare
    url = "http://localhost:8080/statistics/gethumidday?year=2016&month=7&buildingId=20"

    # Senza cache
    start_time = time.time()
    response = requests.get(url)
    end_time = time.time()
    execution_time = end_time - start_time 
    print(f"Execution time: {execution_time} seconds")

    # Con cache
    start_time = time.time()
    response = requests.get(url)
    end_time = time.time()
    execution_time = end_time - start_time 
    print(f"Execution time: {execution_time} seconds")

