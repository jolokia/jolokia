package org.jolokia.client.jmxadapter;

public class FieldWithMoreElementsThanTheTypeImpl implements FieldWithMoreElementsThanTheType {

  final String theFieldThatIsDefined;
  final String anotherFieldThatIsNotDefined;

  public FieldWithMoreElementsThanTheTypeImpl(String theFieldThatIsDefined,
      String anotherFieldThatIsNotDefined) {
    this.theFieldThatIsDefined = theFieldThatIsDefined;
    this.anotherFieldThatIsNotDefined = anotherFieldThatIsNotDefined;
  }

  @Override
  public String getTheFieldThatIsDefined() {
    return this.theFieldThatIsDefined;
  }

  public String getAnotherFieldThatIsNotDefined() {
    return anotherFieldThatIsNotDefined;
  }
}
