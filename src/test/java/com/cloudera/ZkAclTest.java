package com.cloudera;

import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ZkAclTest {
    @Test
    public void simpleWorldAclParsing() {
        testAcl(new String[][]{{"world", "anyone", "a"}});
    }

    @Test
    public void singlePermParsing() {
        testAcl(new String[][]{
                {"scheme1", "create-only-perm", "c"},
                {"scheme2", "delete-only-perm", "d"},
                {"scheme3", "read-only-perm", "r"},
                {"scheme4", "write-only-perm", "w"},
                {"scheme5", "admin-only-perm", "a"}
        });
    }

    @Test
    public void allPermsParsing() {
        testAcl(new String[][]{
                {"sasl", "all-perms", "warcd"}
        });
    }

    @Test
    public void cnUserParsing() {
        testAcl(new String[][]{
                {"x509", "CN=user1-simple-name", "rwa"},
                {"x509", "CN=user2, DC=long, DC=name", "cdr"},
                {"x509", "CN=user3:name, DC=with, DC=colon", "rw"}
        });
    }

    private void testAcl(String[][] aclInputs) {
        String[] inputs = new String[aclInputs.length];
        int index = 0;
        for (String[] aclInput : aclInputs) {
            String scheme = aclInput[0];
            String principal = aclInput[1];
            String permsStr = aclInput[2];
            inputs[index++] = scheme + ":" + principal + ":" + permsStr;
        }
        List<ACL> acls = ZkAcl.parseAcls(inputs);
        assertEquals("ACLs not parsed", aclInputs.length, acls.size());
        index = 0;
        for (ACL acl : acls) {
            String scheme = aclInputs[index][0];
            String principal = aclInputs[index][1];
            String permsStr = aclInputs[index][2];
            int perms =
                    (permsStr.contains("c") ? ZooDefs.Perms.CREATE : 0) |
                    (permsStr.contains("d") ? ZooDefs.Perms.DELETE : 0) |
                    (permsStr.contains("r") ? ZooDefs.Perms.READ : 0) |
                    (permsStr.contains("a") ? ZooDefs.Perms.ADMIN : 0) |
                    (permsStr.contains("w") ? ZooDefs.Perms.WRITE : 0);
            assertEquals(String.format("Wrong scheme (%s)", inputs[index]), scheme, acl.getId().getScheme());
            assertEquals(String.format("Wrong principal (%s)", inputs[index]), principal, acl.getId().getId());
            assertEquals(String.format("Wrong perms (%s)", inputs[index++]), perms, acl.getPerms());
        }
    }
}
