package org.jolokia.service.serializer.json;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import javax.management.ObjectName;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jolokia.json.JSONArray;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.service.serializer.object.ObjectToObjectConverter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.testng.Assert.*;

public class MapAccessorTest extends AbstractObjectAccessorTest {

    Map<String, Object> map;

    @BeforeMethod
    public void setUp() throws Exception {
        map = new HashMap<>();
        map.put("eins","one");
        map.put("zwei","second");
        map.put("drei",new ObjectName("test:type=blub"));
        map.put("vier",Boolean.TRUE);
    }

    @Test
    public void testSimple() throws Exception {
        JSONObject object = (JSONObject) extractJson(map);
        assertEquals(object.size(),4);
        assertTrue((Boolean) object.get("vier"));
    }

    @Test
    public void testTooLong() throws Exception {
        map = new LinkedHashMap<>();
        map.put("eins", "one");
        map.put("zwei", "second");
        map.put("drei", new ObjectName("test:type=blub"));
        map.put("vier", Boolean.TRUE);
        map.put("funf", "5");
        map.put("sechs", "sześć");

        JSONObject object = (JSONObject) extractJson(map);
        assertEquals(object.size(), 5);
        assertFalse(object.containsKey("sechs"));
    }

    @Test
    public void nonStringKeys() throws Exception {
        // we should be able to handle keys of various types which could be serializable to String
        Map<Object, Object> map = new HashMap<>();
        map.put(1, "one");
        map.put(2L, "second");
        map.put(new Date(), new ObjectName("test:type=blub"));
        map.put(Inet4Address.getByAddress("localhost", new byte[] { 0x7f, 0, 0, 1 }), Boolean.TRUE);
        map.put(new ObjectName("test:type=borg"), "5");

        JSONObject object = (JSONObject) extractJson(map);
        assertEquals(object.size(), 5);
        assertFalse(object.containsKey("sechs"));
    }

    @Test
    public void allSupportedConvertersWithMapKeys() throws Exception {
        converter = new ObjectToJsonConverter(objectToObjectConverter, null, new TestJolokiaContext() {
            @Override
            public String getConfig(ConfigKey pKey) {
                if (pKey == ConfigKey.DATE_FORMAT) {
                    return "yyyy>MM>dd~HH.mm.ss (X)";
                }
                if (pKey == ConfigKey.DATE_FORMAT_ZONE) {
                    return "UTC";
                }
                if (pKey == ConfigKey.SERIALIZE_LONG) {
                    return "string";
                }
                return super.getConfig(pKey);
            }
        });
        converter.setupContext(new SerializeOptions.Builder()
            .useAttributeFilter(true)
            .serializeLong("string")
            .maxCollectionSize(0)
            .build());

        Map<Object, Object> imTheMap = new HashMap<>();
        JSONObject json;

        // built-in SimplifierAccessors

        // Class
        imTheMap.put(String.class, String.class);
        json = (JSONObject) extractJson(imTheMap);
        JSONObject theClass = (JSONObject) json.get("java.lang.String");
        assertEquals(theClass.get("name"), "java.lang.String");
        assertEquals(((JSONArray) theClass.get("interfaces")).size(), 5);

        // Element
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element el1 = doc.createElementNS("urn:jolokia", "root");
        doc.appendChild(el1);
        Element el2 = doc.createElement("no-namespace-child");
        el1.appendChild(el2);
        imTheMap.clear();
        imTheMap.put(el1, el1);
        imTheMap.put(el2, el2);
        json = (JSONObject) extractJson(imTheMap);
        JSONObject el1JSON = (JSONObject) json.get("{urn:jolokia}root");
        JSONObject el2JSON = (JSONObject) json.get("no-namespace-child");
        assertEquals(el1JSON.get("name"), "root");
        assertEquals(el1JSON.get("namespace"), "urn:jolokia");
        assertTrue((Boolean) el1JSON.get("hasChildNodes"));
        assertEquals(el2JSON.get("name"), "no-namespace-child");
        assertNull(el2JSON.get("namespace"));
        assertFalse((Boolean) el2JSON.get("hasChildNodes"));

        // File
        File f = new File(".");
        imTheMap.clear();
        imTheMap.put(f, f);
        json = (JSONObject) extractJson(imTheMap);
        assertTrue((Boolean) ((JSONObject) (json.get(f.getCanonicalPath()))).get("directory"));

        // InetAddress
        imTheMap.clear();
        InetAddress ip4 = Inet4Address.getByAddress(new byte[]{ (byte) (192 & 0xff), (byte) (168 & 0xff), 0, 0 });
        InetAddress ip6 = Inet6Address.getByAddress(new byte[]{ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 });
        imTheMap.put(ip4, ip4);
        imTheMap.put(ip6, ip6);
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(((JSONObject) (json.get("192.168.0.0"))).get("address"), "192.168.0.0");
        assertEquals(((JSONObject) (json.get("102:304:506:708:90a:b0c:d0e:f10"))).get("address"), "102:304:506:708:90a:b0c:d0e:f10");

        // Module
        imTheMap.clear();
        imTheMap.put(String.class.getModule(), String.class.getModule());
        json = (JSONObject) extractJson(imTheMap);
        assertFalse(((JSONArray) (((JSONObject) json.get("java.base")).get("packages"))).isEmpty());

        // ObjectName
        ObjectName on1 = ObjectName.getInstance("jolokia:type=Test");
        imTheMap.clear();
        imTheMap.put(on1, on1);
        json = (JSONObject) extractJson(imTheMap);
        // ObjectNameSimplifier is disabled in src/test/resources/META-INF/jolokia/simplifiers
        assertEquals(((JSONObject) (json.get("jolokia:type=Test"))).get("domain"), "jolokia");

        // Package
        imTheMap.clear();
        imTheMap.put(String.class.getPackage(), String.class.getPackage());
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(((JSONObject) json.get("java.lang")).get("package"), "java.lang");

        // URI - new in 2.4.0 - just String conversion without simplifier/accessor
        URI uri = URI.create("urn:jolokia");
        imTheMap.clear();
        imTheMap.put(uri, uri);
        imTheMap.put(f.getCanonicalFile().toURI(), f.getCanonicalFile().toURI());
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(json.get("urn:jolokia"), "urn:jolokia");
        assertEquals(json.get(f.getCanonicalFile().toURI().toString()), f.getCanonicalFile().toURI().toString());

        // URL
        URL url = f.getCanonicalFile().toURI().toURL();
        imTheMap.clear();
        //noinspection UrlHashCode
        imTheMap.put(url, url);
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(((JSONObject) (json.get(f.getCanonicalFile().toURI().toString()))).get("url"), f.getCanonicalFile().toURI().toString());

        // built-in ObjectAccessors that support String conversion

        // Calendar
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d = format.parse("2025-09-16 11:42:00.103");
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        imTheMap.clear();
        imTheMap.put(c, c);
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(json.get("2025>09>16~11.42.00 (Z)"), "2025>09>16~11.42.00 (Z)");

        // Date - but should be overridden by TestSimplifier
        imTheMap.clear();
        imTheMap.put(d, d);
        json = (JSONObject) extractJson(imTheMap);
        // overridden by src/test/resources/META-INF/jolokia/simplifiers
        // and serialize Long as String
        assertEquals(((JSONObject) json.get("UNIX-time:" + d.getTime())).get("millis"), Long.toString(d.getTime()));

        // Enum
        imTheMap.clear();
        imTheMap.put(ConfigKey.AGENT_ID, ConfigKey.AGENT_DESCRIPTION);
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(json.get("AGENT_ID"), "AGENT_DESCRIPTION");

        // java.time Temporals - all of them
        ZoneId zone = ZoneId.of("CET"); // summer time on 2025-09-16

        Instant instant = Instant.ofEpochMilli(d.getTime());
        imTheMap.clear();
        imTheMap.put(instant, instant);
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(json.get("2025>09>16~11.42.00 (Z)"), "2025>09>16~11.42.00 (Z)");

        LocalDate localDate = LocalDate.of(2025, 9, 16);
        imTheMap.clear();
        imTheMap.put(localDate, localDate);
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(json.get("2025-09-16"), "2025-09-16");

        LocalTime localTime = LocalTime.of(11, 42, 0);
        imTheMap.clear();
        imTheMap.put(localTime, localTime);
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(json.get("11:42:00"), "11:42:00");

        LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
        imTheMap.clear();
        imTheMap.put(localDateTime, localDateTime);
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(json.get("2025>09>16~11.42.00 (Z)"), "2025>09>16~11.42.00 (Z)");

        Year year = Year.of(2025);
        imTheMap.clear();
        imTheMap.put(year, year);
        json = (JSONObject) extractJson(imTheMap);
        // kind of unexpected, but it's String just like other temporals
        assertEquals(json.get("2025"), "2025");

        YearMonth yearMonth = YearMonth.of(2025, 9);
        imTheMap.clear();
        imTheMap.put(yearMonth, yearMonth);
        json = (JSONObject) extractJson(imTheMap);
        assertEquals(json.get("2025-09"), "2025-09");

        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, localTime, zone);
        imTheMap.clear();
        imTheMap.put(zonedDateTime, zonedDateTime);
        json = (JSONObject) extractJson(imTheMap);
        // formatter uses UTC when parsing ZonedDateTime for CET summer time, so -2 hours
        assertEquals(json.get("2025>09>16~09.42.00 (Z)"), "2025>09>16~09.42.00 (Z)");

        OffsetDateTime offsetDateTime = OffsetDateTime.of(localDate, localTime, zone.getRules().getOffset(instant));
        imTheMap.clear();
        imTheMap.put(offsetDateTime, offsetDateTime);
        json = (JSONObject) extractJson(imTheMap);
        // formatter uses UTC when parsing ZonedDateTime for CET summer time, so -2 hours
        assertEquals(json.get("2025>09>16~09.42.00 (Z)"), "2025>09>16~09.42.00 (Z)");

        OffsetTime offsetTime = OffsetTime.of(localTime, offsetDateTime.getOffset());
        imTheMap.clear();
        imTheMap.put(offsetTime, offsetTime);
        json = (JSONObject) extractJson(imTheMap);
        // offset taken from OffsetTime, not from the formatter because we use DateTimeFormatter.ISO_OFFSET_TIME
        assertEquals(json.get("11:42:00+02:00"), "11:42:00+02:00");

        // other
        imTheMap.clear();
        imTheMap.put(Boolean.TRUE, Boolean.FALSE);
        imTheMap.put('a', 'b');
        // all 6 unboxed
        imTheMap.put((byte) 42, (byte) 43);
        imTheMap.put((short) 43, (short) 44);
        imTheMap.put(44, 45);
        imTheMap.put(45L, 46L);
        imTheMap.put(47.1f, 47.1f);
        imTheMap.put(47.2, 48.2);
        imTheMap.put(new BigInteger("64598723469582734695872364"), new BigInteger("64598723469582734695872364"));
        imTheMap.put(new BigDecimal("64598723469582734695872364.42"), new BigDecimal("64598723469582734695872364.42"));
        imTheMap.put(new BigDecimal("123.42"), new BigDecimal("45234582734658723645987263948756928374659827346598273645.2"));
        json = (JSONObject) extractJson(imTheMap);
        // json = {org.jolokia.json.JSONObject@3036}  size = 12
        // {@3301} "44" -> {java.lang.Long@3302} 45
        // {@3303} "a" -> {@3304} "b"
        // {@3305} "45" -> {java.lang.Long@3306} 46
        // {@3307} "e581a291-7a17-45bb-9628-07492809ba24" -> {org.jolokia.json.JSONObject@3308}  size = 2
        // {@3309} "ja" -> {org.jolokia.json.JSONObject@3310}  size = 15
        // {@3311} "true" -> {java.lang.Boolean@3312} false
        // {@3315} "64598723469582734695872364" -> {java.math.BigInteger@3316} "64598723469582734695872364"
        // {@3317} "64598723469582734695872364.42" -> {java.math.BigDecimal@3318} "64598723469582734695872364.42"
        // {@3319} "42" -> {java.lang.Long@3320} 43
        // {@3313} "47.1" -> {java.math.BigDecimal@3314} "47.09999847412109375"
        // {@3321} "43" -> {java.lang.Long@3322} 44
        // {@3323} "47.2" -> {java.math.BigDecimal@3324} "48.2000000000000028421709430404007434844970703125"
        assertSame(json.get("true"), Boolean.FALSE);
        assertEquals(json.get("a"), "b");
        // these are byte/short/int converted to long, but because these are really NOT longs, they're not affected
        // by "serializeLong" option which is for JavaScript that doesn't handle the range of Longs as Java...
        assertEquals(json.get("42"), 43L);
        assertEquals(json.get("43"), 44L);
        assertEquals(json.get("44"), 45L);
        // serialize Long = "string"
        assertEquals(json.get("45"), "46");
        assertEquals(json.get("47.1"), new BigDecimal("47.1"));
        assertEquals(json.get("47.2"), new BigDecimal("48.2"));
        // serialize Long = "string"
        assertEquals(json.get("64598723469582734695872364"), new BigInteger("64598723469582734695872364"));
        assertEquals(json.get("123.42"), new BigDecimal("45234582734658723645987263948756928374659827346598273645.2"));

        // two more
        imTheMap.clear();
        imTheMap.put(Locale.JAPANESE, Locale.FRANCE);
        UUID uuid = UUID.randomUUID();
        imTheMap.put(uuid, uuid);
        json = (JSONObject) extractJson(imTheMap);

        assertEquals(json.get("ja"), "fr_FR");
        assertEquals(json.get(uuid.toString()), uuid.toString());
    }

    @Test
    public void testWithPath() throws Exception {
        assertEquals(extractJson(map,"drei","domain"),"test");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithInvalidPath() throws Exception {
        extractJson(map,"fuenf");
    }

    @Test
    public void testWithWildcardPath() throws Exception {
        JSONObject result = (JSONObject) extractJson(map,null,"domain");
        assertEquals(result.size(), 1);
        assertEquals(result.get("drei"),"test");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithInvalidWildcardPath() throws Exception {
        extractJson(map, null, "domain2");
    }

    @Test
    public void testSetValue() throws Exception {
        assertTrue(objectAccessor.canSetValue());
        objectAccessor.setObjectValue(objectToObjectConverter, map, "eins", "une");
        assertEquals(map.get("eins"), "une");
    }

    @Override
    ObjectAccessor createExtractor() {
        return new MapAccessor();
    }

}
