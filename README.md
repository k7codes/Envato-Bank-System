# Envato Bank

Veri güvenliğine azami önem veren, çift kayıtlı (double-entry) bir banka çekirdeği.
Java/Spring Boot REST API + C# WPF (MVVM) masaüstü istemci + PostgreSQL.

## Mimari

```
┌────────────────────────┐        HTTPS/JSON        ┌───────────────────────────┐
│  EnvatoBank.Desktop     │  ─────────────────────▶  │  bank-api (Spring Boot)    │
│  C# WPF · MVVM          │   Bearer JWT (access)     │  Modüler monolit + REST    │
│  Login/Hesaplar/Transfer│  ◀─────────────────────  │  JWT + Argon2 + RBAC       │
└────────────────────────┘                          └────────────┬──────────────┘
                                                                  │ JDBC
                                                       ┌──────────▼──────────┐
                                                       │  PostgreSQL 17       │
                                                       │  Flyway migrations   │
                                                       └──────────────────────┘
```

- **Para modeli:** tutarlar `BIGINT` *minor unit* (kuruş/cent) olarak saklanır; kayan noktalı sayı kullanılmaz.
- **Çift kayıtlı ledger:** her transfer bir `DEBIT` + bir `CREDIT` satırı üretir; iki hesap aynı işlemde (transaction) güncellenir.
- **Değiştirilemez kayıtlar:** `ledger_entries` ve `audit_logs` tablolarında append-only trigger (UPDATE/DELETE yasak) ve audit için SHA-256 **hash-chain** (her kayıt öncekinin hash'ini içerir).

## Teknoloji yığını

| Katman  | Teknoloji                                                                  |
|---------|----------------------------------------------------------------------------|
| Backend | Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA, Flyway       |
| Kimlik  | JWT (jjwt 0.12.6), Argon2id parola hash, refresh-token rotasyonu           |
| Kripto  | AES-256-GCM (PII şifreleme) + HMAC-SHA-256 blind index (Bouncy Castle)     |
| Veritabanı | PostgreSQL 17                                                           |
| İstemci | C# / .NET 9 WPF, sade MVVM (harici MVVM kütüphanesi yok)                   |

## Dizin yapısı

```
backend/   Spring Boot REST API (com.envatobank.api)
desktop/   C# WPF istemci (EnvatoBank.Desktop)
```

## Ön koşullar

- JDK 21 (`JAVA_HOME` ayarlı)
- PostgreSQL 17 çalışıyor olmalı (`postgresql-x64-17` servisi)
- .NET 9 SDK (masaüstü istemci için)

## Kurulum ve çalıştırma

### 1) Veritabanı

```sql
CREATE ROLE bank_app LOGIN PASSWORD '<parola>';
CREATE DATABASE envato_bank OWNER bank_app;
-- Testler için:
CREATE DATABASE envato_bank_test OWNER bank_app;
```

### 2) Backend yapılandırması

`backend/.env` dosyasını `backend/.env.example`'dan türetin ve doldurun:

```
DB_URL=jdbc:postgresql://127.0.0.1:5432/envato_bank
DB_USERNAME=bank_app
DB_PASSWORD=<parola>
JWT_SECRET=<base64, en az 32 byte>
PII_ENCRYPTION_KEY=<base64, tam 32 byte>
PII_INDEX_KEY=<base64, en az 16 byte>
```

Base64 anahtar üretimi (PowerShell):

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

### 3) Backend'i çalıştır

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
.\mvnw.cmd spring-boot:run        # backend dizininde
```

Flyway migrasyonu ve demo verisi (seeder) otomatik uygulanır. API: `http://127.0.0.1:8080`

### 4) Testler

```powershell
.\mvnw.cmd test                    # backend dizininde
```

- Birim testleri: `CryptoService`, `JwtService`, `MoneyUtil`/`Hashing`
- Entegrasyon testi (gerçek PostgreSQL `envato_bank_test` + MockMvc): login, hesap listeleme, atomik + idempotent transfer, yetki/yetersiz bakiye senaryoları

### 5) Masaüstü istemci

```powershell
dotnet run --project .\desktop\EnvatoBank.Desktop   # kök dizinde
```

API adresi varsayılan `http://127.0.0.1:8080/`; `ENVATO_API_URL` ortam değişkeni ile değiştirilebilir.

## Demo hesaplar

| Rol       | E-posta                     | Parola       |
|-----------|-----------------------------|--------------|
| Müşteri   | `ayse@example.com`          | `Demo!2345`  |
| Müşteri   | `mehmet@example.com`        | `Demo!2345`  |
| Personel  | `employee@envatobank.local` | `Employ!2345`|
| Yönetici  | `admin@envatobank.local`    | `Admin!2345` |

Seeder, `app.seed.enabled=false` ile kapatılabilir.

## API uçları

| Yöntem | Yol                                    | Yetki               |
|--------|----------------------------------------|---------------------|
| POST   | `/api/auth/login`                       | Herkese açık        |
| POST   | `/api/auth/refresh`                     | Herkese açık        |
| POST   | `/api/auth/logout`                      | Kimliği doğrulanmış |
| GET    | `/api/accounts`                         | Kimliği doğrulanmış |
| GET    | `/api/accounts/{id}`                    | Hesap sahibi/Personel |
| GET    | `/api/accounts/{id}/statement`          | Hesap sahibi/Personel |
| POST   | `/api/customers/{id}/accounts`          | Personel/Yönetici   |
| POST   | `/api/transfers`                        | Kimliği doğrulanmış |
| GET    | `/api/transfers`                        | Kimliği doğrulanmış |
| GET    | `/api/transfers/{id}`                   | İşlem sahibi/Personel |

Transfer isteği idempotenttir: `idempotencyKey` aynı istek için tekrar gönderildiğinde yeni para hareketi oluşmaz, ilk işlemin sonucu döner.

## Güvenlik notları (KVKK / GDPR odaklı)

- **PII şifreleme:** e-posta, TC kimlik no ve telefon alanları AES-256-GCM ile şifrelenir; eşitlik araması HMAC-SHA-256 *blind index* ile yapılır.
- **Parolalar:** Argon2id (salt 16, hash 32, memory 64 MiB, iterasyon 3).
- **Oturum:** kısa ömürlü access token (15 dk) + dönebilen refresh token (30 gün); refresh-token yeniden kullanım tespiti (reuse detection) ve hesap kilitleme.
- **Yetkilendirme:** rol tabanlı (CUSTOMER/EMPLOYEE/ADMIN); müşteri yalnızca kendi hesap/işlemlerine erişir.
- **Denetim izi (audit):** tüm kritik işlemler append-only + hash-chain log'a yazılır; geçmiş bir kaydın değiştirilmesi zinciri kırar.
- **Bütünlük:** para transferi tek transaction'da; hesaplar deterministik sırayla `PESSIMISTIC_WRITE` ile kilitlenir (deadlock ve yarış koşulu önlenir).
- **Yapılandırma:** `spring.jpa.hibernate.ddl-auto=validate`, Jackson `fail-on-unknown-properties`; sır anahtarları yalnızca `.env` (gitignore'lu) üzerinden.

## Lisans

MIT — bkz. [LICENSE](LICENSE).

## Bilinen sınırlar / sonraki adımlar

- Çoklu para birimi kayıtları ayrı hesaplardadır; FX (döviz çevrimi) desteklenmez.
- Ledger `balance_after_minor` değeri işlem anında hesaplanır; büyük ölçekli raporlama için materialized view önerilir.
- HTTPS/TLS sonlandırma ters proxy (nginx) ile yapılmalı; istemci-API arası üretimde TLS şarttır.
- Rol yönetimi ekranı ve müşteri self-servis kayıt henüz istemcide yok.
