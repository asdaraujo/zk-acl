package com.cloudera;

import org.apache.commons.cli.*;
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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class ZkAcl implements Watcher {
    private static final String ADDAUTH_DIGEST_CONFIG = "addauth.digest";

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        Options options = new Options();

        Option zkOption = new Option("z", "zookeeper", true, "ZooKeeper address (host:port).");
        zkOption.setRequired(true);
        options.addOption(zkOption);
        Option configOption = new Option("c", "config", true, "Path of the properties file.");
        configOption.setRequired(true);
        options.addOption(configOption);
        Option znodeOption = new Option("n", "znode", true, "Znode path.");
        znodeOption.setRequired(true);
        options.addOption(znodeOption);
        Option recursiveOption = new Option("r", "recursive", false, "Executes the command recursively through the znode tree.");
        recursiveOption.setRequired(false);
        options.addOption(recursiveOption);
        Option verboseOption = new Option("v", "verbose", false, "Lists znodes during a set operation.");
        verboseOption.setRequired(false);
        options.addOption(verboseOption);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.setSyntaxPrefix("Usage: ");
            String header = "Sets or retrieves ACLs for ZNodes in ZooKeeper.\n\n";
            formatter.printHelp(String.format("%s [OPTIONS] [<acl> ...]", ZkAcl.class.getName()), header, options, null, false);

            System.exit(1);
        }

        String propsFile = cmd.getOptionValue("config");
        String hostPort = cmd.getOptionValue("zookeeper");
        String znode = cmd.getOptionValue("znode");
        boolean recursive = cmd.hasOption("recursive");
        boolean verbose = cmd.hasOption("verbose");

        args = cmd.getArgs();
        List<ACL> acls = parseAcls(args);

        Properties props = loadProperties(propsFile);
        processAcls(hostPort, props.getProperty(ADDAUTH_DIGEST_CONFIG, null), znode, acls, recursive, verbose);
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

    private static void processAcls(String hostPort, String addAuthDigest, String znode, List<ACL> acls, boolean recursive, boolean verbose)
            throws KeeperException, InterruptedException, IOException {
        ZooKeeper zk = null;
        try {
            Watcher watcher = new ZkAcl();
            zk = new ZooKeeper(hostPort, 3000, watcher);

            if (addAuthDigest != null) {
                zk.addAuthInfo("digest", addAuthDigest.getBytes());
            }

            if (acls.size() > 0) {
                System.out.println("Setting ACLs...");
            } else {
                System.out.println("Retrieving ACLs...");
            }
            processZnode(zk, znode, acls, recursive, verbose);
        } finally {
            if (zk != null)
                zk.close();
        }
    }

    private static void processZnode(ZooKeeper zk, String znode, List<ACL> acls, boolean recursive, boolean verbose)
            throws KeeperException, InterruptedException, IOException {
        if (zk.exists(znode, false) == null) {
            System.out.printf("ZNode %s doesn't exist", znode);
        } else {
            if (acls.size() > 0) {
                zk.setACL(znode, acls, -1);
                if (verbose)
                    printACLs(znode, acls);
            } else {
                List<ACL> getAcls = zk.getACL(znode, null);
                printACLs(znode, getAcls);
            }
        }
        if (recursive) {
            List<String> children = zk.getChildren(znode, false);
            for (String child : children) {
                String childPath = znode + "/" + child;
                processZnode(zk, childPath, acls, recursive, verbose);
            }
        }

    }

    public static List<ACL> parseAcls(String[] args) {
        List<ACL> acls = new ArrayList<ACL>();
        for(int i = 0; i < args.length; i++) {
            int firstSep = args[i].indexOf(":");
            int lastSep = args[i].lastIndexOf(":");
            if (firstSep == -1 || lastSep == -1 || firstSep == lastSep)
                throw new RuntimeException(String.format("Invalid ACL. Expected format: scheme:id:perms. Found: %s", args[i]));
            String scheme = args[i].substring(0, firstSep);
            String principal = args[i].substring(firstSep+1, lastSep);
            String permsStr = args[i].substring(lastSep+1);
            acls.add(new ACL(stringToPerms(permsStr), new Id(scheme, principal)));
        }
        return acls;
    }

    public static void printACLs(String znode, List<ACL> acls) {
        System.out.printf("%s\n", znode);
        for (ACL a : acls) {
            printACL(a);
        }
    }

    public static void printACL(ACL acl) {
        String perms = permsToString(acl.getPerms());
        System.out.printf("  Scheme: %s, Id: %s, Perms: %s\n", acl.getId().getScheme(), acl.getId().getId(), perms);
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