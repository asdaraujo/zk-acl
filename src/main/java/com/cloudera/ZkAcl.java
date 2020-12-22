package com.cloudera;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
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
    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        ZooKeeper zk = null;
        if (args.length < 3) {
            System.err.println(String.format("Syntax: %s <properties_file> <zk_host>:<zk_port> <znode> [<acl> ...]",
                    ZkAcl.class.getName()));
            System.exit(1);
        }
        String propsFile = args[0];
        String hostPort = args[1];
        String znode = args[2];
        try (InputStream input = new FileInputStream(propsFile)) {
            Properties props = new Properties();
            props.load(input);
            System.setProperties(props);
        }
        try {
            Watcher watcher = new ZkAcl();
            zk = new ZooKeeper(hostPort, 3000, watcher);
            zk.addAuthInfo("digest", "super:cloudera".getBytes());
            Stat s = zk.exists(znode, watcher);
            if (s == null) {
                System.out.println("ZNode doesn't exist");
            } else {
                List<ACL> acls = null;
                if (args.length > 1) {
                    acls = new ArrayList<ACL>();
                    for(int i = 1; i < args.length; i++) {
                        String[] tokens = args[i].split(":");
                        acls.add(new ACL(stringToPerms(tokens[2]), new Id(tokens[0], tokens[1])));
                    }
                    printACLs(acls);
                    zk.setACL(znode, acls, -1);
                }
                acls = zk.getACL(znode, null);
                printACLs(acls);
            }
        } catch (Exception e) {
            System.out.println("EXC");
            System.out.println(e.toString());
            throw e;
        } finally {
            if (zk != null)
                zk.close();
        }
    }

    public static void printACLs(List<ACL> acls) {
        for (ACL a : acls) {
            printACL(a);
        }
    }

    public static void printACL(ACL acl) {
        String perms = permsToString(acl.getPerms());
        System.out.println(String.format("Scheme: %s, Id: %s, Perms: %s", acl.getId().getScheme(), acl.getId().getId(), perms));
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