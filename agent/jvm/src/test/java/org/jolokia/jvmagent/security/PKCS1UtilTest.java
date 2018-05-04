package org.jolokia.jvmagent.security;/*
 * 
 * Copyright 2014 Roland Huss
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

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;

import org.jolokia.util.Base64Util;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author roland
 * @since 01/10/15
 */
public class PKCS1UtilTest {

    public String PRIVATE_KEY =
            "MIIEpQIBAAKCAQEA2N/GkK5d93FnqtPggmZqJYceXkfetsIKMA/BAFqmlHvGFKp6\n" +
            "5wHPZvjqYkbhL2uDO4DT94ZfDtd0KMkDdn64DkPTUxq3G8TnTFUh26RdRNMPyRcV\n" +
            "AEZLOz6c5MhwlLIyAaA8oSoddSFGkERgY3GhNNnMeoRkgdgcGRyez26q3kPHn21D\n" +
            "0wZb9Li8tS3QjUZ/urE7Jy+3ElAD3CJ4AmBLAoyCrca4TjNUCBZdhYBf91PguYCg\n" +
            "CowhciBrJfetFmEz98PaNdKm476EA8AL7kNpYzFcT4c7gQD6HFaJFKSZemeCKvxH\n" +
            "txaKoaWXgrARiORQIF8kt5XGChc7rbaghOICOQIDAQABAoIBAQCIZr4Zk0GQdqgP\n" +
            "/jCvc0CBl+kWvTcrVQFZVx85XMp2ix57MvoXvsC2cAnig9fvnjwsuYsXnFC/Ie1y\n" +
            "FXNzHKIgfrI0C5JtCbu7+7NO1KLAvcqo3DaeNJfujCPblOGR9D2VXjWj27wpRiN+\n" +
            "azMAeKA+gFmmGQypycVqWeDccCtRnMDFE8FVTqPbOpJDT0jLObGGOzaqk6D/1GGZ\n" +
            "hpdNj03ZDOE7xGHGj8C53r/Kp+pNnmbRbJDw/6i2PMJtQYyycBra85ucb+IaUXSt\n" +
            "8E4bfj6NI+Faln8irkiHY/oCz6astoaO1ZuqbxZBmvvMyxM1IHB2cRykoSILr99w\n" +
            "IlTD3GHdAoGBAPqS47nBfUiUIqmWcWKw1G6KNQIcH1dEBCOfSx1jKnbx4w6H9xej\n" +
            "g2e+3HGqyyBFe8utfPrcRnD43arEEwUrXjUwgc3ZlWBY0XzanX4Z0DkUm8iHHfJo\n" +
            "XD1TyLsfp01fbKdKlFW5BW03Q7FOERXlheGvQynQfUBL53yG0WPTdrnvAoGBAN2S\n" +
            "EI1Cqy8aUL3ESsJ/bKpVgLhTwjovQxqM/uYVN282jI9EFAw+PQdpHyFdCr1fJmpY\n" +
            "nW7aHPYQxlWFQEIcoWROBY76VJMpxjLR1LPHV9ezzD9LoFINEGlZu5ZnQasNgFpX\n" +
            "5KjpGdlfa0MzPjauqxr7mUfvnLHolNmwubG9Pk5XAoGBAMACx5aUepifS2CA9CoY\n" +
            "LvD132Dag/mvGSzi6ACA+Q1klgWQkvv+RLe/PdWsdzMni5GsQ9VH7oKrcdFlpt2T\n" +
            "OgGwRgej8B+AcCcorv7ucO0MqcOkJoKXDffAuFUMEHvt36jiMYDu4wWqD6lSlS0e\n" +
            "UNV8JA9qwFAA2kZGWTYR2SzpAoGAH4efj1qDXaqS/s4mDVNwtTSBorlYlEsRc3/I\n" +
            "7hjq0IqkqeZ4K93XdWyCH49L7fLSVqPRk2q6YFG2x4i0wjOsy8dGhzgcPOze5XBy\n" +
            "ojqlx24wjHlIkSSGx1cbmKWM9LhxIWoMgfTZ1tL7Qo7SNZnZg3d2MoRoefCs7eV2\n" +
            "J1LUwPUCgYEAi4tPG9rpYMpD2gQ8u3G3GSWo+8mZOZ4s8/126ozoL4Uuwc1GwJ4o\n" +
            "HQ1qb6Er+XpE+QpOP1A164pmWkKo90wuDWdgXma45veNVSCblHvppSKFGeRaPr3P\n" +
            "i+L8P99EaGlPVUrgpwLVFs03SKmiEVjqDDlXw2+Yiu+9xmW5Pesb1tA=";

    @Test
    public void simple() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        RSAPrivateCrtKeySpec spec = PKCS1Util.decodePKCS1(Base64Util.decode(PRIVATE_KEY));
        assertNotNull(spec);
        assertEquals(new BigInteger("65537"),spec.getPublicExponent());
        assertEquals(new BigInteger("17219073728753538877351436880713775379656064037529658955194218882887990854659277" +
                                    "14926866974743136717398950769494187526607437089900175320890853259761202632924910" +
                                    "04313716240382424077365899979237567821238335068353270396753262938612364085446101" +
                                    "06439976729028133029376341040827650031161824951684561808300496926367402762121624" +
                                    "84716729598919532254889795000011013630896579601633649087197314473243762156761264" +
                                    "28213494680134367573578684374434499094707511493610021020953064686646715594465961" +
                                    "57768682849057097693703094001987209424161987563379538988444327461934240605326845" +
                                    "582692396320855699767767177230708015442200422586240491997"),
                     spec.getPrivateExponent());
    }
}
