# --- Build stage -----------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies in their own layer — they change far less often
# than source code, so this avoids re-downloading the internet on
# every source edit.
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage -----------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/order-management-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
