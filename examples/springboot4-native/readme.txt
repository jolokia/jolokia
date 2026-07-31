This example shows how to use Jolokia with Spring Boot 4 and Spring Web MVC (using @EnableWebMvc annotation).

Build the native image with:

    mvn -Pnative clean package

Run it with:

    ./target/jolokia-example-springboot4-native

And browse to http://localhost:8181/
