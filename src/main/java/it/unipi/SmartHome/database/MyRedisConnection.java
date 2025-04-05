package it.unipi.SmartHome.database;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MyRedisConnection {
    
    public static JedisCluster connectToJedisCluster() {

        Set<HostAndPort> jedisClusterNodes = new HashSet<HostAndPort>();
        jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7001));
        jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7002));
        jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7003));

        System.out.println("Connecting to Redis Cluster requested");
        JedisCluster jedis = new JedisCluster(jedisClusterNodes);
        return jedis;

    }

    public static void closeJedisCluster(JedisCluster jedis) {
        
        System.out.println("Closing JedisCluster connection");
        if (jedis != null) {
            jedis.close();
        }

    }

}
