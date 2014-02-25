package org.jolokia.agent.core.util;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 17.12.13
 */
public class RealmUtilTest {

    @Test
    public void extractRealm() throws MalformedObjectNameException {

        String[] data = {
                "java.lang:type=memory", null,"java.lang:type=memory",
                "proxy@java.lang:type=memory", "proxy", "java.lang:type=memory",
                "spring@:name=myBean","spring",":name=myBean",
                "spring@spring@bla:type=blub","spring","spring@bla:type=blub"
        };
        for (int i = 0; i < data.length; i += 3) {
            for (RealmUtil.RealmObjectNamePair pair : new RealmUtil.RealmObjectNamePair[]
                    {
                            RealmUtil.extractRealm(data[i]),
                            RealmUtil.extractRealm(new ObjectName(data[i]))
                    }) {
                assertEquals(pair.getRealm(),data[i+1],"Realm expected: " + data[i+1]);
                assertEquals(pair.getObjectName(), new ObjectName(data[i + 2]), "Objectname exepcted: " + data[i + 1]);
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*not be null.*")
    public void nullName() throws MalformedObjectNameException {
        RealmUtil.extractRealm((String) null);
    }


    @Test
    public void matchesRealm() throws MalformedObjectNameException {
        String[] data = {
                null,"java.lang:type=memory","true",
                "proxy","java.lang:type=memory","false",
                "proxy","proxy@java.lang:type=memory","true",
                "spring","spring@proxy@java.lang:type=memory","true",
                null,":type=memory","true",
                "spring",":type=memory","false"
        };
        for (int i = 0; i < data.length; i += 3) {
            assertEquals(RealmUtil.matchesRealm(data[i],new ObjectName(data[i+1])),Boolean.parseBoolean(data[i+2]),
                         data[i+1] + " matches realm " + data[i] + ": " + data[i+2]);
        }
    }
}
