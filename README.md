KV Database:
[1] Last reading of a given sensor -> ho pensato di procedere così 'reading:<sensorID>:last' = <stringa da 
concatenare> e nel caso missasse si va a leggere dal document DB, il KV è aggiornato da POST /readings che 
supponiamo aggiunga letture sempre piu' recenti e mai antecedenti
(Se un sensore viene eliminato e se ne crea uno con lo stesso ID potrebbe essere un problema)

[2] Building that a given user belongs to -> ho pensato di procedere così 'building:<username>:buildings' = <risposta>
POST /building, POST /building/user DELETE /building invalidano la cache 
GET /buildings se fa miss lo salva

[3] Retrieval of statistics results -> ho pensato 'statistics:<timestamp>:<buildingID>' = <result>
se si fa miss allora vengono letti dal database e poi inseriti (non devono essere aggiornati)
il timestamp potrebbe essere 'YYYY-MM' e result e' l'oggetto e' il JSON (?) oppure il Document (?) oppure 
il metodo ritorna document.toJson() quindi puo' essere una stringa 

DA FARE:
[1] Repliche (Dio bono)
[2] Macchina Virtuale
[3] Indici
