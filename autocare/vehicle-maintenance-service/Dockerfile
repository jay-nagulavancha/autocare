# Stage 1: build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: runtime
FROM eclipse-temurin:17-jre-alpine
# Minor non-functional change to trigger CI build.
ARG AUTOCARE_GIT_COMMIT=docker
ARG AUTOCARE_BUILD_TIME=unknown
ENV AUTOCARE_GIT_COMMIT=$AUTOCARE_GIT_COMMIT \
    AUTOCARE_BUILD_TIME=$AUTOCARE_BUILD_TIME
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
