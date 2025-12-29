# 1. Build Aşaması (Maven ile)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Bağımlılıkları indir (Cache layer)
RUN mvn dependency:go-offline
COPY src ./src
COPY web ./web
# Fat JAR oluştur (Tüm kütüphaneler içinde)
# Not: pom.xml'de maven-shade-plugin veya assembly-plugin olmalı.
# Basitlik için derleyip classpath ayarlayacağız.
RUN mvn package -DskipTests

# 2. Çalışma Aşaması
FROM eclipse-temurin:17-jre
WORKDIR /app

# Target klasöründeki jar'ı al (İsmi değişebilir, kontrol et)
COPY --from=build /app/target/*-jar-with-dependencies.jar /app/app.jar
# Eğer shade plugin yoksa normal jar ve lib klasörünü kopyalamak gerekir.
# Biz varsayalım ki basit maven build yaptın.

COPY --from=build /app/web /app/web
RUN mkdir -p /app/shared_videos

# Portlar
EXPOSE 8080 50000

CMD ["java", "-jar", "/app/app.jar"]