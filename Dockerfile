# 1. Derleme Aşaması
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Kaynak kodları kopyala
COPY src /app/src

# Çıktı klasörü oluştur ve Java dosyalarını derle
RUN mkdir -p /app/out
# Tüm .java dosyalarını bul ve listeye yaz, sonra derle
RUN find src -name "*.java" > sources.txt && javac -d /app/out @sources.txt

# 2. Çalışma Aşaması
FROM eclipse-temurin:17-jre
WORKDIR /app

# Derlenmiş sınıfları kopyala
COPY --from=build /app/out /app/classes

# Main sınıfını çalıştır
CMD ["java", "-cp", "/app/classes", "com.p2pstream.Main"]