### KV Database:
##### 1. Last reading of a given sensor
la chiave e' 'reading:\<sensorID\>:last' = \<stringa da concatenare\> e nel caso missasse si va a leggere dal document DB. 
La chiave è aggiornato da POST /readings che supponiamo aggiunga letture sempre piu' recenti e mai antecedenti.
(Se un sensore viene eliminato e se ne crea uno con lo stesso ID potrebbe essere un problema ma supponiamo non accada)

##### [2] Building that a given user belongs to
la chiave e' 'building:\<username\>:buildings' = \<risposta\>
POST /building, POST /building/user DELETE /building invalidano la cache mentre GET /buildings se la chiave non e' presente la imposta

##### [3] Retrieval of statistics results
la chiave e' 'statistics:\<year\>:\<month\>:\<buildingID\>:\<name\>' = \<result\> (soggetta a leggeri cambiamenti)
Se la chiave e' impostata allora si salva il valore cachato mentre se non lo e' allora viene fatta la query e impostata la chiave.
Per come sono strutturate le query agiscono su range temporali passati e per come e' pensato l'utilizzo dell'API non e' possibile inserire letture passate quindi non c'e' bisogno di invalidare

### Indici:
##### username di Users: 
CON: 
verrebbe scritto solo da /signup che e' un'operazione velocissima, che avviene molto pocodi frequente e che non va assolutamente ottimizzata quindi il rallentamento di quest'operazione e' trascurabile 
PRO: 
ottimizza POST /login (che possiamo supporre avvenga abbastanza spesso), ottimizza POST /building, DELETE /building, POST /building/user, perché devono modificare il campo buildings dell'utente e ottimizza anche GET /buildings 
##### buildingId di Buildings:
CON:
verrebbe scritto solo da POST /building che e' un operazione velocissima che avviene molto poco quindi il rallentamento e' trascurabile
PRO:
 velocizzerebbe DELETE /building perché deve controllare che l'edificio esista (filtra sull'id), ottimizzerebbe POST /building/user perché deve accedere al building dall'ID per aggiungere l'utente e controllare l'admin, ottimizzerebbe POST /building/sensor perché filtra su buildingID per controllare che l'utente sia admin e aggiungere il sensore, ottimizzerebbe DELETE /building/sensor perché filtra su buildingID per controllare che l'utente sia admin e rimuovere il sensore, ottimizzerebbe POST /reading perché filtra su buildingID per controllare che l'utente sia admin (questa e' la piu' importante perche' rappresenta il carico maggiore)
##### id di Sensors:
CON:
rallenterebbe la POST /building/sensor che e' un'operazione veloce e che non avviene molto spesso
PRO:
ottimizzerebbe la POST /reading che controlla che il sensore sia nel building (verifica che l'id sia in sensors) (questa e' l'operazione piu' importante perche' rappresenta il carico maggiore)

### Repliche MongoDB
Esistono tre repliche di cui un master e due slave e un ulteriore server arbiter che serve per evitare tie break nella decisione di un nuovo master quando il precedente e' morto.
Le repliche hanno priorita' diversa (serve a decidere chi diventa master)
Il tipo di eventual consistency e' la write monotonic garantita dal fatto che solamente il master puo' accettare le scritture.
Write concern deve essere w=1 (avendo tantissime scritture di letture di sensori possiamo permetterci di perderne qualcuna ma non possiamo permetterci che siano lente)
Bisogna settare un wtimeout alto poiché l'operazione di eliminazione di un edificio non e' immediata (cio' non e' un problema poiché non avviene quasi mai)

##### Comandi Linux
Creo le directory dove verranno salvati i dati
```bash
mkdir ~/data/r1
mkdir ~/data/r2
mkdir ~/data/r3
mkdir ~/data/arb
```
Faccio partire i daemon per le 3 repliche e l'arbitro
```bash
mongod --replSet rs0 --dbpath ~/data/r1 --port 27018 --bind_ip localhost --oplogSize 200
mongod --replSet rs0 --dbpath ~/data/r2 --port 27019 --bind_ip localhost --oplogSize 200
mongod --replSet rs0 --dbpath ~/data/r3 --port 27020 --bind_ip localhost --oplogSize 200
mongod --replSet rs0 --dbpath ~/data/arb --port 27021 --bind_ip localhost --oplogSize 200 
```
Accedo alla shell di mongodb e configuro il replica set
```bash
mongosh --port 27018
rsconf = {
    _id: "rs0",
    members: [
        { _id: 0, host: "localhost:27018", priority: 2 },
        { _id: 1, host: "localhost:27019", priority: 1 },
        { _id: 2, host: "localhost:27020", priority: 0 },
        { _id: 3, host: "localhost:27021", arbiterOnly: true }
    ],
    settings: {
        setDefaultRWConcern: {w: 1, wtimeout: 10000}
    }
};
rs.initiate(rsconf);
rs.status(); # to check the status
rs.reconfig(rsconf);
```
Per accedere al cluster di server con Java basta cambiare la URI
```java
ConnectionString uri = new ConnectionString("mongodb://localhost:27018");
MongoClientSettings mcs = MongoClientSettings.builder()
    .applyConnectionString(uri)
    .readPreference(ReadPreference.nearest())
    .retryWrites(true)
    .writeConcern(WriteConcern.ACKNOWLEDGED) // Usa le impostazioni di default date dal server
    .build();
```
### MongoDB sharding
Andrebbe spiegato perché non conviene farlo
### Repliche Redis
Non mi sembra veramente necessario



### DA FARE:
1. Deployment su Cluster locale, quindi gestire le repliche sia per mongoDB che per Redis (bisogna avere almeno tre repliche per mongoDB e valurare per entrambi l'eventual consistency) e valutare se implementare lo sharding (quindi pensiamo una possibile soluzione di sharding poi l'analizziamo e valutiamo se vale la pena implementarla o meno) 
[Fatto tutto bisogna solo argomentare MongoDB Sharding e fare Repliche ed Eviction Redis]
2. Deployment sulla Macchina Virtuale (bisogna sentire il Ducange)
3. Indici, bisogna discuterne e poi fare delle prove con e senza (immagino basti far vedere dei dati che abbiamo ricavato e magari gli script che abbiamo usato per ottenerli)
4. Pensare ai test da fare sull'API durante la presentazione (quindi quale API chiamare, che test fare con POSTMAN e cose varie)
5. Scrivere la Documentazione seguendo la consegna 
6. Fare degli unit test da fargli vedere (tipo codice python che registra l'utente e poi lo fa loggare)
7. Fare statistiche sulle varie operazioni nel DB 

