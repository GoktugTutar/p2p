# ---- build stage
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ---- runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*-shaded.jar /app/p2pstream.jar

# Default: HEADLESS (GUI docker içinde istenirse ayrıca ele alınır)
ENV MODE=HEADLESS

ENTRYPOINT ["java","-jar","/app/p2pstream.jar"]
