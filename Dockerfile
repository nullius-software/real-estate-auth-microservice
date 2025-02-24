FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk AS runtime
WORKDIR /app
COPY --from=build /app/target/auth-microservice-0.0.1-SNAPSHOT.jar app.jar
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]