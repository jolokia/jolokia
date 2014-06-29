package org.jolokia.it;

import java.beans.ConstructorProperties;

/**
 * @author roland
 * @since 29.06.14
 */
public class PojoBean {
     private final String name;
    private final String value;

    @ConstructorProperties({ "name", "value" })
    public PojoBean(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof PojoBean))
            return false;
        PojoBean other = (PojoBean) obj;
        return name.equals(other.name) && value.equals(other.value);
    }

    @Override
    public String toString() {
        return "PojoBean[name=" + name + ",value=" + value + "]";
    }
}
