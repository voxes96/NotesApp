# NotesApp

NotesApp to backendowa aplikacja REST do zarządzania notatkami użytkowników. System obsługuje rejestracje, logowanie, tworzenie i edycje notatek, udostępnianie notatek innym użytkownikom oraz podgląd historii zmian.

---

### Stack Technologiczny

- **Java 21** - najnowsza wersja LTS z nowoczesnymi funkcjami języka
- **Spring Boot 3.5.14** - framework aplikacyjny
- **Spring Security + OAuth2** - autoryzacja i uwierzytelnianie oparte na JWT
- **Spring Data JPA + Hibernate** - warstwa dostępu do danych
- **Hibernate Envers** - audytowanie i historia zmian
- **MySQL** - relacyjna baza danych
- **Bucket4j** - rate limiting
- **JUnit 5, Mockito, MockMvc** - framework do testów
- **Testcontainers** - testy integracyjne z izolowanym środowiskiem



## Architektura

Projekt jest zbudowany jako monolityczna aplikacja Spring Boot z architektura warstwowa.

```text
Klient HTTP
   |
   v
Controllers - publiczne endpointy REST
   |
   v
DTO - kontrakty wejścia i wyjścia API
   |
   v
Services - logika biznesowa i reguły dostępu
   |
   v
Repositories - dostęp do danych przez Spring Data JPA
   |
   v
Entities - model domenowy zapisywany w MySQL
```

Taki podział oddziela odpowiedzialności: kontrolery odpowiadają za komunikację HTTP, serwisy za decyzje biznesowe, repozytoria za trwały zapis danych, a encje za strukturę domeny. Dzięki temu kod jest łatwiejszy do testowania, rozwoju i utrzymania.


### Główne endpointy:
```
POST   /register                    - Rejestracja użytkownika
POST   /login                       - Logowanie
POST   /items                       - Tworzenie notatki
GET    /items                       - Lista notatek użytkownika
PATCH  /items/{id}                  - Aktualizacja notatki
DELETE /items/{id}                  - Usunięcie notatki
GET    /items/{id}/history          - Historia zmian
POST   /items/{id}/share            - Udostępnienie notatki
DELETE /items/{id}/share/{userId}   - Cofnięcie dostępu
```

### Decyzje projektowe

#### GlobalExceptionHandler
- centralna obsługa wyjątków
- jednolity format odpowiedzi JSON
- łatwość obsługi błędów oraz ewentualnej późnieszej rozbudowy

#### Użycie DTO zamiast bezpośredniego działania na encjach
- API nie ujawnia wewnetrznego modelu bazy danych
- automatyczna walidacja (np. poprzez @Valid)
- oddzielenie od logiki biznesowej
- użycie records - zapewnienie immutability i mniej bolierplate'u

#### Bezpieczeństwo
- hasla sa hashowane algorytmem BCrypt
- użycie OAuth2 - zapewnia natywne wsparcie Spring Security, minimalna początkowa konfiguracja (którą można rozbudować), automatyczna walidacja JWT przy każdym żądaniu
- endpointy `/register` i `/login` są publiczne, a pozostałe wymagają uwierzytelnienia
- automatyczne wyciąganie użytkownika z `Authentication` (poprzez Spring Security)


## Konfiguracja

**application.properties:**

- **spring.application.name** - nazwa aplikaci
- **server.port** - port na którym wystawione jest API
- **spring.datasource.url=** - ścieżka do połączenia do bazy banych
- **spring.datasource.username=** - użytkownik bazy danych
- **spring.datasource.password=** - hasło użytkownika
- **jwt.secret=** - sekretny klucz służący do szyfrowania tokenów JWT
- **jwt.expiration=** - czas po którym token JWT wygaśnie (w sekundach)
- **rate-limit.login.capacity=** - maksymalna ilość prób logowania
- **rate-limit.login.refill-tokens=** - ilość prób logowania o które jest uzupełniana pula po zadanym czasie `refill-minutes`
- **rate-limit.login.refill-minutes=** - czas po którym uzupełniana jest pula prób logowania o `refill-tokens`

```
UWAGA: W produkcji zmienić:
- spring.jpa.show-sql=false - służy do debugowania
- spring.jpa.hibernate.ddl-auto=validate - w przyszłości najlepiej użyć flyway do aktualizacji bazy danych
```


## Uruchomienie Projektu

### Konfiguracja bazy:
```sql
CREATE DATABASE notes_app;
CREATE USER 'app_user'@'localhost' IDENTIFIED BY 'secure_password';
GRANT ALL PRIVILEGES ON notes_app.* TO 'app_user'@'localhost';
```

### Uruchomienie:
```bash
# Build
mvn clean install

# Run
mvn spring-boot:run

# Tests
mvn test
```

### Testowanie API:
```bash
# Rejestracja
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d '{"login":"user1","password":"password123"}'

# Login
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"login":"user1","password":"password123"}'

# Tworzenie notatki (z tokenem)
curl -X POST http://localhost:8080/items \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"My Note","content":"Note content"}'
```
