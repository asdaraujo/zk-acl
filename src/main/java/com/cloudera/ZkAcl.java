package com.cloudera;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.KeeperException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ZkAcl implements Watcher {
    private static final String ADDAUTH_DIGEST_CONFIG = "addauth.digest";
    private static final int FIRST_ACL_POS = 3;

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        if (args.length < 3) {
            System.err.printf("Syntax: %s <properties_file> <zk_host>:<zk_port> <znode> [<acl> ...]\n",
                    ZkAcl.class.getName());
            System.exit(1);
        }
        String propsFile = args[0];
        String hostPort = args[1];
        String znode = args[2];
        List<ACL> acls = parseAcls(args);

        Properties props = loadProperties(propsFile);
        processAcls(hostPort, znode, props.getProperty(ADDAUTH_DIGEST_CONFIG, null), acls);
    }

    private static Properties loadProperties(String propsFile) throws IOException {
        try (InputStream input = new FileInputStream(propsFile)) {
            Properties props = new Properties();
            props.load(input);
            for(String prop : props.stringPropertyNames()) {
                System.setProperty(prop, (String) props.get(prop));
            }
            return props;
        }
    }

    private static void processAcls(String hostPort, String znode, String addAuthDigest, List<ACL> acls)
            throws KeeperException, InterruptedException, IOException {
        ZooKeeper zk = null;
        try {
            Watcher watcher = new ZkAcl();
            zk = new ZooKeeper(hostPort, 3000, watcher);

            if (addAuthDigest != null) {
                zk.addAuthInfo("digest", addAuthDigest.getBytes());
            }

            if (zk.exists(znode, watcher) == null) {
                System.out.println("ZNode doesn't exist");
            } else {
                if (acls.size() > 0) {
                    System.out.println("Setting ACLs:");
                    zk.setACL(znode, acls, -1);
                } else {
                    System.out.println("Retrieving ACLs:");
                    acls = zk.getACL(znode, null);
                }
                printACLs(acls);
            }
        } finally {
            if (zk != null)
                zk.close();
        }
    }

    private static List<ACL> parseAcls(String[] args) {
        List<ACL> acls = new ArrayList<ACL>();
        for(int i = FIRST_ACL_POS; i < args.length; i++) {
            String[] tokens = args[i].split(":");
            if (tokens.length != 3)
                throw new RuntimeException(String.format("Invalid ACL. Expected format: scheme:id:perms. Found: %s", args[i]));
            acls.add(new ACL(stringToPerms(tokens[2]), new Id(tokens[0], tokens[1])));
        }
        return acls;
    }

    public static void printACLs(List<ACL> acls) {
        for (ACL a : acls) {
            printACL(a);
        }
    }

    public static void printACL(ACL acl) {
        String perms = permsToString(acl.getPerms());
        System.out.printf("Scheme: %s, Id: %s, Perms: %s\n", acl.getId().getScheme(), acl.getId().getId(), perms);
    }

    public static String permsToString(int perms) {
        StringBuilder p = new StringBuilder();
        if ((perms & ZooDefs.Perms.CREATE) != 0) {
            p.append('c');
        }
        if ((perms & ZooDefs.Perms.DELETE) != 0) {
            p.append('d');
        }
        if ((perms & ZooDefs.Perms.READ) != 0) {
            p.append('r');
        }
        if ((perms & ZooDefs.Perms.WRITE) != 0) {
            p.append('w');
        }
        if ((perms & ZooDefs.Perms.ADMIN) != 0) {
            p.append('a');
        }
        return p.toString();
    }

    public static int stringToPerms(String permString) {
        int perm = 0;
        for (int i = 0; i < permString.length(); i++) {
            switch (permString.charAt(i)) {
                case 'r':
                    perm |= ZooDefs.Perms.READ;
                    break;
                case 'w':
                    perm |= ZooDefs.Perms.WRITE;
                    break;
                case 'c':
                    perm |= ZooDefs.Perms.CREATE;
                    break;
                case 'd':
                    perm |= ZooDefs.Perms.DELETE;
                    break;
                case 'a':
                    perm |= ZooDefs.Perms.ADMIN;
                    break;
                default:
                    System.err.println("Unknown perm type: " + permString.charAt(i));
            }
        }
        return perm;
    }

    public void process(WatchedEvent e) {
    }
}