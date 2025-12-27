# P2P peer'leri GUI destekli çalıştırma

Projede artık iki tip peer var:

- **Headless peer**: GUI yok. Docker içinde çalışır, UDP portu 50000 üzerinden dinler.
- **GUI peer**: Swing arayüzüyle `DISCOVER`/`SEARCH` flood'larını elle tetikler ve cevapları ekranda gösterir.

## Docker ile headless peer'leri başlatma

```bash
docker compose up --build -d peer-1 peer-2
```

- Her container kendi statik IP'siyle (`172.25.0.2`, `172.25.0.3`) ayrı bir makine gibi davranır.
- Host üzerinden erişmek için UDP portları sırasıyla `50001` ve `50002` olarak yayınlanır.
- Eğer otomatik arama yapan bir üçüncü peer isterseniz `initiator` profilini açabilirsiniz:

```bash
docker compose --profile initiator up --build -d
```

Bu peer `INITIATOR=true` ve `SEARCH_QUERY=YTlogo` ile açılır.

## GUI peer'i çalıştırma (host makine)

1. Kodları derleyin:
   ```bash
   find src/main/java -name "*.java" > sources.txt
   javac -d out @sources.txt
   ```
2. GUI'yi başlatın (varsayılan arama ifadesi değişebilir):
   ```bash
   GUI_MODE=true SEARCH_QUERY=YTlogo java -cp out com.p2pstream.Main
   ```
3. GUI'deki butonlarla `DISCOVER` veya `SEARCH` flood'u başlatın. Docker peer'leri, statik IP'leri ve host port yönlendirmeleri sayesinde sanki farklı makineler gibi yanıt verir.

> Not: GUI peer de UDP 50000 portunu kullanır. Aynı makinede birden fazla GUI peer açacaksanız farklı port için kodu veya ortam değişkenlerini uyarlamanız gerekir.
