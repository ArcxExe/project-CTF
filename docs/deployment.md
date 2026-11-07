# Руководство по развертыванию (Deployment Guide) — VPS 1 ГБ ОЗУ

Эта заметка описывает процесс развертывания и обновления платформы **project-CTF** на виртуальном сервере (VPS) с ограниченными ресурсами (**1 ГБ ОЗУ**, 1 vCPU, ~9 ГБ SSD).

---

## 1. Главный принцип: почему собираем локально

Сборка проекта из исходников (`gradle bootJar` и `npm run build`) требует до **1–1.5 ГБ ОЗУ**. На сервере с 1 ГБ ОЗУ прямая сборка через обычный `docker-compose.yml` приведет к нехватке памяти (OOM Killer) и зависанию сервера.

Поэтому используется двухэтапная схема:
1. **Тяжелая компиляция выполняется локально** (на рабочей машине разработчика):
   * Backend компилируется в `backend/build/libs/ctf-platform-backend-0.1.0-SNAPSHOT.jar`.
   * Frontend собирается в статику `frontend/dist/`.
2. **На VPS отправляются уже готовые артефакты**, которые упаковываются в легкие рантайм-образы за пару секунд:
   * [docker-compose.prod.yml](file:///home/arcx/code/project-CTF/docker-compose.prod.yml) — оптимизированный стек с лимитами памяти.
   * [backend/Dockerfile.runtime](file:///home/arcx/code/project-CTF/backend/Dockerfile.runtime) — только `eclipse-temurin:21-jre-alpine` + готовый `.jar`.
   * [frontend/Dockerfile.runtime](file:///home/arcx/code/project-CTF/frontend/Dockerfile.runtime) — только `nginx:1.27-alpine` + готовая папка `dist/` и [frontend/nginx.conf](file:///home/arcx/code/project-CTF/frontend/nginx.conf).

---

## 2. Оптимизации под 1 ГБ ОЗУ

В файле [docker-compose.prod.yml](file:///home/arcx/code/project-CTF/docker-compose.prod.yml) применены следующие настройки:

* **Backend (Spring Boot 3 / Java 21) — ~310 МБ в рантайме (лимит 480 МБ):**
  * `JAVA_TOOL_OPTIONS`: `-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss256k -XX:TieredStopAtLevel=1`
    * `SerialGC` расходует минимум памяти на метаданные сборщика мусора (в отличие от `G1GC`).
    * `-Xss256k` уменьшает размер стека каждого потока с 1 МБ до 256 КБ.
    * `-XX:TieredStopAtLevel=1` отключает тяжелую C2 JIT-компиляцию, ускоряя старт и экономя ОЗУ.
  * `SERVER_TOMCAT_THREADS_MAX: 30` (вместо 200 по умолчанию).
  * `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 5` (вместо 10 по умолчанию).
  * `SPRING_PROFILES_ACTIVE: prod` (отключает подробный вывод каждого SQL-запроса в консоль).
* **PostgreSQL 16 (`postgres:16-alpine`) — ~40 МБ в рантайме (лимит 160 МБ):**
  * Параметры запуска: `shared_buffers=64MB`, `work_mem=4MB`, `maintenance_work_mem=32MB`, `max_connections=25`.
* **Frontend (`nginx:1.27-alpine`) — ~2.5 МБ в рантайме (лимит 64 МБ):**
  * Раздает статику и проксирует `/api/` и `/actuator/` на `backend:8080`.
  * Проброшены порты `80` (основной HTTP) и `3000`.
  * В [frontend/nginx.conf](file:///home/arcx/code/project-CTF/frontend/nginx.conf) отключена буферизация (`proxy_buffering off`) для работы SSE-стриминга лидерборда и увеличен лимит загрузки файлов (`client_max_body_size 25M`).

---

## 3. Первичная настройка нового сервера (выполняется 1 раз)

Если вы переезжаете на новый чистый VPS, подключитесь к нему по SSH (`ssh root@<IP_СЕРВЕРА>`) и выполните:

### 3.1. Настройка Swap (обязательно до 2 ГБ)
```bash
fallocate -l 1G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=1024
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl vm.swappiness=20
free -h
```

### 3.2. Установка Docker (минимальный набор без лишних пакетов)
На небольших дисках (8–10 ГБ) важно ставить Docker с флагом `--no-install-recommends`, чтобы не тянуть тяжелые зависимости:
```bash
# Если на сервере Debian 11 (bullseye), переключаем репозитории на архивные:
sed -i 's|http://security.debian.org/debian-security|http://archive.debian.org/debian-security|g' /etc/apt/sources.list || true
sed -i 's|http://deb.debian.org/debian|http://archive.debian.org/debian|g' /etc/apt/sources.list || true

apt-get -o Acquire::Check-Valid-Until=false update
apt-get install -y ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list

apt-get -o Acquire::Check-Valid-Until=false update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable --now docker
```

### 3.3. Освобождение 80 порта и создание папки проекта
```bash
systemctl stop nginx apache2 httpd 2>/dev/null || true
systemctl disable nginx apache2 httpd 2>/dev/null || true
mkdir -p /opt/project-ctf
```

---

## 4. Как деплоить и обновлять приложение (пошагово)

Когда вы внесли изменения в код на своем компьютере и хотите обновить версию на сервере:

### Шаг 1. Соберите артефакты локально (на вашем ПК)
Из корня проекта `/home/arcx/code/project-CTF`:
```bash
# 1. Сборка Backend (.jar)
./backend/gradlew -p backend bootJar --no-daemon

# 2. Сборка Frontend (dist/)
npm --prefix frontend run build
```

### Шаг 2. Упакуйте только нужные для рантайма файлы и отправьте на VPS
```bash
# Создаем временную папку со сборкой
rm -rf /tmp/ctf-deploy
mkdir -p /tmp/ctf-deploy/backend/build/libs /tmp/ctf-deploy/frontend

cp docker-compose.prod.yml /tmp/ctf-deploy/docker-compose.yml
cp backend/Dockerfile.runtime /tmp/ctf-deploy/backend/Dockerfile.runtime
cp backend/build/libs/ctf-platform-backend-0.1.0-SNAPSHOT.jar /tmp/ctf-deploy/backend/build/libs/
cp frontend/Dockerfile.runtime frontend/nginx.conf /tmp/ctf-deploy/frontend/
cp -r frontend/dist /tmp/ctf-deploy/frontend/

# Архивация и отправка по SCP
tar -czf /tmp/ctf-deploy.tar.gz -C /tmp/ctf-deploy .
scp /tmp/ctf-deploy.tar.gz root@46.17.46.87:/opt/project-ctf/deploy.tar.gz
```

### Шаг 3. Распакуйте и перезапустите контейнеры на сервере
```bash
ssh root@46.17.46.87 "cd /opt/project-ctf && tar -xzf deploy.tar.gz && rm -f deploy.tar.gz && docker compose up -d --build"
```
*(База данных PostgreSQL хранится в Docker-томе `postgres_data`, поэтому при обычном `docker compose up -d --build` все данные сохраняются, а новые миграции Flyway накатываются автоматически).*

---

## 5. Полезные команды для управления на сервере

Подключитесь к серверу (`ssh root@46.17.46.87`) и перейдите в папку `/opt/project-ctf`:

| Задача | Команда |
| :--- | :--- |
| **Проверить статус контейнеров** | `docker compose ps` |
| **Посмотреть потребление ОЗУ и CPU** | `docker stats --no-stream` |
| **Посмотреть логи Backend в реальном времени** | `docker compose logs -f --tail=100 backend` |
| **Перезапустить только Backend** | `docker compose restart backend` |
| **Проверить здоровье API** | `curl http://localhost/actuator/health` |
| **Очистить старые неиспользуемые образы Docker** | `docker image prune -f` |
| **Сделать бэкап базы данных в файл** | `docker compose exec -T postgres pg_dump -U ctf_user ctf_platform > backup.sql` |
| **Полный сброс БД (удаление тома)** | `docker compose down -v && docker compose up -d` |
