/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.server.core.request.notification;

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.json.simple.JSONArray;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author roland
 * @since 19.03.13
 */
public class NotificationCommandTest {

    @Test
    public void register() throws Exception {

        check(
                new String[]{
                        "command", "register"
                },
                new Checkable<RegisterCommand>() {
                    public void check(RegisterCommand cmd) {
                        Assert.assertEquals(cmd.getType(), NotificationCommandType.REGISTER);
                    }
                });
    }

    @Test
    public void unregister() throws Exception {
        final String uuid = UUID.randomUUID().toString();
        check(
                new String[]{
                        "command", "unregister",
                        "client", uuid
                },
                new Checkable<UnregisterCommand>() {
                    public void check(UnregisterCommand cmd) {
                        assertEquals(cmd.getType(), NotificationCommandType.UNREGISTER);
                        assertEquals(cmd.getClient(), uuid);
                    }
                }

             );
    }

    @Test
    public void ping() throws Exception {
        final String uuid = UUID.randomUUID().toString();
        check(
                new String[]{
                        "command", "ping",
                        "client", uuid
                },
                new Checkable<PingCommand>() {
                    public void check(PingCommand cmd) {
                        assertEquals(cmd.getType(), NotificationCommandType.PING);
                        assertEquals(cmd.getClient(), uuid);
                    }
                }

             );
    }

    @Test
    public void list() throws Exception {
        final String uuid = UUID.randomUUID().toString();
        check(
                new String[]{
                        "command", "list",
                        "client", uuid
                },
                new Checkable<ListCommand>() {
                    public void check(ListCommand cmd) {
                        assertEquals(cmd.getType(), NotificationCommandType.LIST);
                        assertEquals(cmd.getClient(), uuid);
                    }
                }

             );
    }

    @Test
    public void add() throws Exception {
        final String uuid = UUID.randomUUID().toString();
        final ObjectName mbean = new ObjectName("bla:type=blub");
        final String[][] filters = new String[][] {
                { "javax.mbean.register", "javax.mbean.unregister" },
                { "javax.mbean.register"}
        };
        final Object handback = "handback";
        for (String[] filterS : filters) {
            final String[] filter = filterS;
            check(
                    new Object[] {
                            "command", "add",
                            "client", uuid,
                            "mode", "pull",
                            "mbean", mbean.toString(),
                            "filter", getFilterArrayList(filter),
                            "config", "{ \"extra\": \"club\" }",
                            "handback", handback
                    },
                    new Checkable<AddCommand>() {
                        public void check(AddCommand cmd) {
                            assertEquals(cmd.getType(), NotificationCommandType.ADD);
                            assertEquals(cmd.getMode(),"pull");
                            assertEquals(cmd.getClient(),uuid);
                            assertEquals(cmd.getObjectName(),mbean);
                            Map<String,?> config = cmd.getConfig();
                            assertEquals(config.size(),1);
                            assertEquals(config.get("extra"),"club");
                            assertEquals(cmd.getFilter().size(),filter.length);
                            assertEquals(cmd.getHandback(),handback);
                            for (int i = 0; i < filter.length; i++) {
                                assertEquals(cmd.getFilter().get(i),filter[i]);
                            }
                        }
                    }
                 );
        }
    }

    @Test
    public void addWithoutFilterAndHandback() throws Exception {
        final String uuid = UUID.randomUUID().toString();
        final ObjectName mbean = new ObjectName("bla:type=blub");
        check(
                new Object[]{
                        "command", "add",
                        "client", uuid,
                        "mode", "pull",
                        "mbean", mbean.toString(),
                },
                new Checkable<AddCommand>() {
                    public void check(AddCommand cmd) {
                        assertEquals(cmd.getType(), NotificationCommandType.ADD);
                        assertEquals(cmd.getMode(),"pull");
                        assertEquals(cmd.getClient(), uuid);
                        assertEquals(cmd.getObjectName(), mbean);
                    }
                });
    }

    @Test
    public void addWithConfigMap() throws Exception {
        Map<String, Object> args = prepareAddTestMap();
        args.put("config",Collections.singletonMap("fcn","meister"));
        AddCommand command = (AddCommand) NotificationCommandFactory.createCommand(args);
        assertEquals(command.getConfig().size(),1);
        assertEquals(command.getConfig().get("fcn"),"meister");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*\\{not parsable\\{.*")
    public void addWithConfigMapUnparsable() throws Exception {
        Map<String, Object> args = prepareAddTestMap();
        args.put("config","{not parsable{");
        NotificationCommandFactory.createCommand(args);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*\\[\"array\"].*")
    public void addWithConfigMapWrongType() throws Exception {
        Map<String, Object> args = prepareAddTestMap();
        args.put("config","[\"array\"]");
        NotificationCommandFactory.createCommand(args);
    }

    private Map<String, Object> prepareAddTestMap() {
        Map<String, Object> args = new HashMap<>();
        args.put("client", UUID.randomUUID().toString());
        args.put("command","add");
        args.put("mode","pull");
        args.put("mbean","test:type=test");
        return args;
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*MBean.*")
    public void addWithoutMBeanStack() throws Exception {
        Stack<String> args = new Stack<>();
        args.push("pull");
        args.push(UUID.randomUUID().toString());
        args.push("add");
        NotificationCommandFactory.createCommand(args);
    }

    @Test
    public void addWithEmptyFilterAndConfig() throws MalformedObjectNameException {
        Stack<String> args = new Stack<>();
        args.push("{}");
        args.push(" ");
        args.push("test:type=test");
        args.push("pull");
        args.push(UUID.randomUUID().toString());
        args.push("add");
        AddCommand command = (AddCommand) NotificationCommandFactory.createCommand(args);
        assertNull(command.getFilter());
        Map<?, ?> config = command.getConfig();
        assertEquals(config.size(),0);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*MBean.*")
    public void addWithoutMBeanMap() throws Exception {
        Map<String,String> args = new HashMap<>();
        args.put("client",UUID.randomUUID().toString());
        args.put("command","add");
        args.put("mode","pull");
        NotificationCommandFactory.createCommand(args);
    }
    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*mode.*")
    public void addWithoutModeStack() throws Exception {
        Stack<String> args = new Stack<>();
        args.push(UUID.randomUUID().toString());
        args.push("add");
        NotificationCommandFactory.createCommand(args);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*mode.*")
    public void addWithoutModeMap() throws Exception {
        Map<String,String> args = new HashMap<>();
        args.put("client",UUID.randomUUID().toString());
        args.put("command","add");
        NotificationCommandFactory.createCommand(args);
    }

    @Test
    public void remove() throws Exception {
        final String uuid = UUID.randomUUID().toString();
        final String handle = "handle";
        check(
                new Object[] {
                        "command", "remove",
                        "client", uuid,
                        "handle", handle
                },
                new Checkable<RemoveCommand>() {
                    public void check(RemoveCommand cmd) {
                        assertEquals(cmd.getType(), NotificationCommandType.REMOVE);
                        assertEquals(cmd.getClient(),uuid);
                        assertEquals(cmd.getHandle(),handle);
                    }
                }
             );
    }
    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*handle.*")
    public void removeNoHandleStack() throws Exception {
        Stack<String> args = new Stack<>();
        args.push(UUID.randomUUID().toString());
        args.push("remove");
        NotificationCommandFactory.createCommand(args);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*handle.*")
    public void removeNoHandleMap() throws Exception {
        Map<String,String> args = new HashMap<>();
        args.put("client",UUID.randomUUID().toString());
        args.put("command","remove");
        NotificationCommandFactory.createCommand(args);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*client.*")
    public void removeNoClientStack() throws Exception {
        Stack<String> args = new Stack<>();
        args.push("remove");
        NotificationCommandFactory.createCommand(args);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*client.*")
    public void removeNoClientMap() throws Exception {
        Map<String,String> args = new HashMap<>();
        args.put("command","remove");
        NotificationCommandFactory.createCommand(args);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class,expectedExceptionsMessageRegExp = ".*blub.*")
    public void negativeLookup() {
        NotificationCommandType.getTypeByName("blub");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullLookup() {
        NotificationCommandType.getTypeByName(null);
    }


    @SuppressWarnings("unchecked")
    private JSONArray getFilterArrayList(String[] pFilter) {
        JSONArray ret = new JSONArray();
        for (String f : pFilter) {
            ret.add(f);
        }
        return ret;
    }

    // ========================================================================================================

    @SuppressWarnings("unchecked")
    private <T extends NotificationCommand> void check(Object[] pArgs, Checkable<T> pCheckable) throws MalformedObjectNameException {
        pCheckable.check((T) NotificationCommandFactory.createCommand(getMap(pArgs)));
        pCheckable.check((T) NotificationCommandFactory.createCommand(getStack(pArgs)));
    }

    private Stack<String> getStack(Object[] pArgs) {
        List<String> ret = new ArrayList<>();
        ret.add((String) pArgs[1]);
        if (pArgs.length > 3) {
            for (int j = 3; j < pArgs.length; j +=2) {
                if (pArgs[j] instanceof String) {
                    ret.add((String) pArgs[j]);
                } else if (pArgs[j] instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> argsList = (List<String>) pArgs[j];
                    StringBuffer s = new StringBuffer();
                    for (String a : argsList) {
                        s.append(a);
                        s.append(",");
                    }
                    ret.add(s.substring(0,s.length() - 1));
                } else {
                    ret.add(pArgs[j].toString());
                }
            }
        }
        Collections.reverse(ret);
        Stack<String> st = new Stack<>();
        st.addAll(ret);
        return st;
    }

    private interface Checkable<T extends NotificationCommand> {
        void check(T cmd);
    }

    private Map<String, ?> getMap(Object[] pArgs) {
        Map<String, Object> ret = new HashMap<>();
        for (int i = 0; i < pArgs.length; i+=2) {
            ret.put((String) pArgs[i],pArgs[i+1]);
        }
        return ret;
    }
}
