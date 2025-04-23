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
 velocizzerebbe DELETE /building perché deve controllare che l'edificio esista (filtra sull'id), ottimizzerebbe POST /building/user perché deve accedere al building dall'ID per aggiungere l'utente e controllare l'admin, ottimizzerebbe POST /building/sensor perché filtra su buildingID per controllare che l'utente sia admin e aggiungere il sensore, ottimizzerebbe DELETE /building/sensor perché filtra su buildingID per controllare che l'utente sia admin e rimuovere il sensore.
##### id di Sensors:
CON:
rallenterebbe la POST /building/sensor che e' un'operazione veloce e che non avviene molto spesso
PRO:
ottimizzerebbe la POST /reading che controlla che il sensore sia nel building (verifica che l'id sia in sensors) (questa e' l'operazione piu' importante perche' rappresenta il carico maggiore)

### Test durante la presentazione
Durante la presentazione sara' necessario fare dei test per far vedere che l'applicazione funzioni, sicuramente andranno usati gli unit test in un database vuoto per garantire il funzionamento corretto della maggior parte delle API poi andranno fatti dei test su GET /sensors che e' un operazione abbastanza pesante che sfrutta il KV come cache, lo stesso discorso vale per GET /buildings sebbene meno pesante come operazione e per tutte le analytics, ossia GET /statistics/\<analytics_name\>

##### GET /sensors
```bash
curl -X 'GET' \
  'http://localhost:8080/sensors?username=AlexSmith13&buildingID=25' \
  -H 'accept: */*'
curl -X 'GET' \
  'http://localhost:8080/sensors?username=EmmaWilliams17&buildingID=14' \
  -H 'accept: */*'
curl -X 'GET' \
  'http://localhost:8080/sensors?username=JohnJones19&buildingID=12' \
  -H 'accept: */*'
```

##### GET /buildings
```bash
curl -X 'GET' \
  'http://localhost:8080/buildings?username=JohnMiller1' \
  -H 'accept: */*'
curl -X 'GET' \
  'http://localhost:8080/sensors?username=EmmaWilliams17' \
  -H 'accept: */*'
curl -X 'GET' \
  'http://localhost:8080/sensors?username=JohnJHernandez6' \
  -H 'accept: */*'
```
##### GET /statistics/top5powerconsumtpion
```bash
curl -X 'GET' \
  'http://localhost:8080/statistics/top5powerconsumption?buildingID=20&year=2016&month=2&day=1' \
  -H 'accept: */*'
```
##### GET /statistics/rainydays
```bash
curl -X 'GET' \
  'http://localhost:8080/statistics/rainydays?buildingID=20&year=2016&month=2' \
  -H 'accept: */*'
```
##### GET /statistics/powerfromsolarpanels
```bash
curl -X 'GET' \
  'http://localhost:8080/statistics/powerfromsolarpanels?buildingID=2&year=2016&month=6' \
  -H 'accept: */*'
```
##### GET /statistics/peaktemperature
```bash
curl -X 'GET' \
  'http://localhost:8080/statistics/peaktemperature?buildingID=20&year=2016&month=2&day=1' \
  -H 'accept: */*'
```
##### GET /statistics/peakpowerconsumptionhours
```bash
curl -X 'GET' \
  'http://localhost:8080/statistics/peakpowerconsumptionhours?buildingID=20&year=2016&month=2&day=3' \
  -H 'accept: */*'
```
##### GET /statistics/mosthumidday
```bash
curl -X 'GET' \
  'http://localhost:8080/statistics/mosthumidday?buildingID=20&year=2016&month=2' \
  -H 'accept: */*'
```
Poi occorre svolgere dei test in cui si fa vedere che il database riesce a reggere delle scritture simultanee da tutti i sensori mentre si svolgono anche altre richieste. 
Le scritture simultanee e cadenzate vengono gestite con uno script in python che legge un file JSON con le letture di ogni sensore e le manda facendo una POST a /reading, poi si addormenta per 2 minuti (anche se dovrebbero essere 10) e rimanda le stesse letture ciclicamente.
Quando il server e' sotto sforzo si svolgeranno le richieste "di routine" a mano, per semplicita' possiamo prenderne alcune tra le precedenti.


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
rs.status(); 
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
Si puo' fare uno sharding di Readings usando buildingID come partition key poiché è l'unico modo per distribuire il carico di scritture dovute alle letture dei sensori. Oltretutto, la partition key dovra' essere hashata e per agevolare la scalabilita' orizzontale abbiamo deciso di usare il consistent hashing.
SensorID sarebbe meglio perché evita che building molto grossi intasino dei server però chiamando getUserSensors si dovrebbe leggere su piu' server per avere una risposta

### Eviction Policy Redis
Si setta in questo modo
```bash
CONFIG SET maxmemory-policy allkeys-lfu
```
Abbiamo scelta questa configurazione poiche' non riteniamo ci siano chiavi che hanno un'importanza maggiore rispetto ad altre e nessuna di esse risulta essere critica ai fini della corretta esecuzione dell'applicativo, basti pensare che le analytics (carico maggiore dell'applicazione) eseguene in circa una decina di millisecondi.

### Repliche Redis

Ci saranno 3 server (che partizionano le richieste in maniera uniforme) ognuno con una replica.
Bisogna creare le cartelle per i 6 server.
```bash
mkdir 7000
mkdir 7001
mkdir 7002
mkdir 7003
mkdir 7004
mkdir 7005
```
Poi creare un redis.conf all'interno di ciascuna directory cambiando la porta
```bash
port 7000
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
appendonly yes
```
Poi rimanendo nella directory bisogna usare il comando
```bash
redis-server ./redis.conf
```
E infine per far partire il cluster bisogna usare il seguente comando
```bash
redis-cli --cluster create 127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005 --cluster-replicas 1
```
Per connettersi da Java usare questa funzione:
```java
    private JedisCluster connectToJedisCluster() {

        Set<HostAndPort> jedisClusterNodes = new HashSet<HostAndPort>();
        jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7001));
        jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7002));
        jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7003));

        JedisCluster jedis = new JedisCluster(jedisClusterNodes);
        return jedis;

    }
```

### Docker 
Per buildare l'immagine:
```bash 
  docker build -t spring-dev .
```
Per eseguire il container 
```bash 
  docker run --network=host -p 8080:8080 spring-dev
```


