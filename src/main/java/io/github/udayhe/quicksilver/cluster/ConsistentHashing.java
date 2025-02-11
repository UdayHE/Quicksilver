package io.github.udayhe.quicksilver.cluster;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConsistentHashing<K> {

    private static final Logger log = Logger.getLogger(ConsistentHashing.class.getName());
    private final SortedMap<Integer, ClusterNode> ring = new TreeMap<>();

    public void addNode(ClusterNode node) {
        int hash = node.hashCode();
        ring.put(hash, node);
        log.log(Level.INFO, "🖥️ Node added to hash ring: {0}", node);
    }

    public void removeNode(ClusterNode node) {
        int hash = node.hashCode();
        ring.remove(hash);
        log.log(Level.INFO, "❌ Node removed from hash ring: {0}", node);
    }

    public ClusterNode getNodeForKey(K key) {
        if (ring.isEmpty()) return null;
        int hash = key.hashCode();
        SortedMap<Integer, ClusterNode> tailMap = ring.tailMap(hash);
        int nodeHash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        return ring.get(nodeHash);
    }
}
