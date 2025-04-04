La collection Readings e' di tipo Time-series quindi ha un indice sul timestamp ed un altro sul metaField, in questo tipo di collection non e' possibile introdurre altri indici poiche' i dati sono ottimizzati in "bucket" ossia raggruppamenti con timestamp vicini e stesso metaField

Query 1:
    Con Indice: 92ms
    Senza Indice: 11s
Query 2:
    Con Indice: 11ms
    Senza Indice: 7s
Query 3:
    Con Indice: 10ms 
    Senza Indice: 8s 
Query 4:
    Con Indice: 21ms 
    Senza Indice: 8s 
Query 5:
    Con Indice: 10ms
    Senza Indice: 8s
Query 6:
    Con Indice: 2ms
    Senza Indice: 8s