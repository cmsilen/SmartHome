KV Database:
[1] Last reading of a given sensor -> ho pensato di procedere così 'reading:<sensorID>:last' = <stringa da 
concatenare> e nel caso missasse si va a leggere dal document DB, il KV è aggiornato da POST /readings che 
supponiamo aggiunga letture sempre piu' recenti e mai antecedenti
(Se un sensore viene eliminato e se ne crea uno con lo stesso ID potrebbe essere un problema)

[2] Building that a given user belongs to -> ho pensato di procedere così 'building:<username>:buildings' = <risposta>
POST /building, POST /building/user DELETE /building invalidano la cache 
GET /buildings se fa miss lo salva

[3] Retrieval of statistics results -> ho pensato 'statistics:<year>:<month>:<buildingID>:<name>' = <result>
se si fa miss allora vengono letti dal database e poi inseriti (non devono essere aggiornati)

Indici:
[1] username di Users: 
[1.1] verrebbe scritto solo da /signup che e' un'operazione velocissima, che avviene molto poco
di frequente e che non va assolutamente ottimizzata quindi il rallentamento di quest'operazione e' trascurabile 
[1.2] ottimizza POST /login (che possiamo supporre avvenga abbastanza spesso), ottimizza POST /building, 
DELETE /building, POST /building/user, perché devono modificare il campo buildings dell'utente e ottimizza 
anche GET /buildings 
[2] buildingId di Buildings:
[2.1] verrebbe scritto solo da POST /building che e' un operazione velocissima che avviene molto poco 
quindi il rallentamento e' trascurabile
[2.2] velocizzerebbe DELETE /building perché deve controllare che l'edificio esista (filtra sull'id), ottimizzerebbe
POST /building/user perché deve accedere al building dall'ID per aggiungere l'utente e controllare l'admin,
ottimizzerebbe POST /building/sensor perché filtra su buildingID per controllare che l'utente sia admin e 
aggiungere il sensore, ottimizzerebbe DELETE /building/sensor perché filtra su buildingID per controllare che 
l'utente sia admin e rimuovere il sensore, ottimizzerebbe POST /reading perché filtra su buildingID per controllare
che l'utente sia admin (questa e' la piu' importante perche' rappresenta il carico maggiore)
[3] sensors di Buildings:
[3.1] rallenterebbe la POST /building/sensor che e' un'operazione veloce e che non avviene molto spesso
[3.2] ottimizzerebbe la POST /reading che controlla che il sensore sia nel building (verifica che l'id sia in sensors)
(questa e' l'operazione piu' importante perche' rappresenta il carico maggiore)


DA FARE:
[1] Repliche (Dio bono)
[2] Macchina Virtuale
[3] Indici
