FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -DskipTests clean package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --create-home spring

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080
USER spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

