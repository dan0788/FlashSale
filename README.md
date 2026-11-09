# ⚡ Flash Sale & High-Performance Order Processing System

Sistema distribuido de procesamiento de ventas flash y pedidos de alta concurrencia desarrollado con **Java 21**, **Spring Boot 4**, **Redis**, **RabbitMQ**, **PostgreSQL** y **Flyway**.

Este proyecto resuelve el desafío crítico de arquitectura que enfrentan plataformas como Amazon, MercadoLibre o Alibaba durante eventos de alta demanda (como el *Black Friday* o *Cyber Monday*): **¿Cómo atender a miles de usuarios que intentan comprar el mismo producto en el mismo segundo sin colapsar la base de datos ni generar sobreventa (*overselling*)?**

---

## 🏛️ Arquitectura del Sistema

```
                      [ 10,000 Peticiones Concurrentes ]
                                     │ (HTTP POST /order)
                                     ▼
                        [ Spring Boot REST API ]
                                     │
           ┌─────────────────────────┴─────────────────────────┐
           ▼ (Paso 1: Memoria RAM < 0.2 ms)                     ▼ (Paso 2: Buffer Asíncrono)
      [ REDIS ]                                         [ RABBITMQ ]
(Script de Lua Atómico:                              (TopicExchange -> Binding -> Queue:
 Valida stock en RAM y descuenta.                     Amortigua la carga y libera
 Si no hay stock, rebota de inmediato                 al cliente con HTTP 202 ACCEPTED)
 sin tocar disco duro)                                         │
                                                               ▼ (Paso 3: A ritmo controlado)
                                                     [ CONSUMIDOR EN SEGUNDO PLANO ]
                                                               │
                                                               ▼
                                                      [ BASE DE DATOS POSTGRESQL ]
                                                      - Resta atómica en disco
                                                      - Optimistic Locking (@Version)
                                                      - Pedido confirmado (Order)
```

---

## 💡 Conceptos Fundamentales y Analogías del Mundo Real

Para comprender a fondo por qué cada componente fue diseñado de esta manera, el sistema utiliza las siguientes analogías del mundo real:

### 1. El Problema de la Base de Datos Relacional y el Almacén
* **La Analogía:** PostgreSQL es como el **almacén gigante en el sótano**. Es seguro, ordenado y permanente, pero lento para que 10,000 personas bajen corriendo las escaleras a tocar la misma caja al mismo tiempo.
* **El Problema Técnico:** Si todas las peticiones van directo a la base de datos relacional, se genera un cuello de botella por bloqueo de filas (*row-level locks*) y saturación del pool de conexiones (*HikariCP*). El servidor colapsa arrojando errores `504 Gateway Timeout` o, peor aún, vendiendo más unidades de las existentes (*race conditions*).

### 2. El Pizarrón Acrílico en el Mostrador (Redis en RAM)
* **La Analogía:** Redis es un **pizarrón acrílico con plumón** pegado en el mostrador del cajero.
* **Concepto:** Antes de abrir la tienda, se hace un **Pre-calentamiento (*Warm-up*)**: se lee el stock de PostgreSQL y se anota en el pizarrón de Redis.
* **Velocidad:** Como Redis opera 100% en memoria RAM, puede realizar más de 100,000 operaciones por segundo con latencias de submilisegundo (< 0.2 ms). Si el pizarrón llega a `0`, las siguientes 9,900 personas son rechazadas inmediatamente sin molestar a la base de datos.

### 3. El Riel de un Solo Vagón (Script Atómico de Lua en Redis)
* **La Analogía:** El torniquete del metro o un riel por donde solo cabe un vagón a la vez en fila india.
* **Concepto:** Si dos procesos leen el stock al mismo tiempo (`GET`) y luego descuentan (`DECR`), ambos ven stock disponible y sobrevenden. Un **script de Lua** se ejecuta de forma indivisible (atómica) y monohilo dentro de Redis: verifica la existencia y descuenta en una sola instrucción sin que nadie pueda meterse en el medio.

### 4. El Tubo Neumático de Futurama y la Cocina (RabbitMQ)
* **La Analogía:** La oficina de recepción y la cocina de un restaurante:
  * **Cajera (API REST):** Cobra y reserva el producto en 1 milisegundo. Le entrega al cliente un ticket y le dice *"Su pedido está en proceso"* (`HTTP 202 Accepted`).
  * **La Ranura de Entrada (`TopicExchange`):** Es la puerta por donde la cajera introduce la cápsula.
  * **La Etiqueta (`RoutingKey`):** Es el código postal o sello de destino pegado en la cápsula (`flash.sale.order.routingKey`).
  * **El Tubo Guiador (`Binding`):** Es el ducto físico que une la ranura con la cesta de la cocina según la etiqueta.
  * **La Cesta de Espera (`Queue`):** La caja durable donde caen las cápsulas y esperan en fila sin perderse si se corta la luz (`durable = true`).
  * **El Cocinero (`OrderConsumer`):** Un hilo en segundo plano que saca las comandas una a una de la cesta y las guarda tranquilamente en PostgreSQL sin sobrecargar el servidor.

### 5. Formularios Públicos vs. Comandas Oficiales (DTOs)
* **`FlashSaleRequest`:** El formulario que llena el cliente desde su celular. Solo contiene lo que el usuario decide (`userId`, `flashSaleProductId`, `quantity`) y cuenta con validaciones de seguridad estrictas (`@Min(1)`, `@NotNull`). Nunca se permite que el cliente envíe precios.
* **`OrderMessage`:** La comanda oficial sellada que viaja por RabbitMQ. Contiene el precio oficial auditado por el servidor, el cálculo matemático del total (`totalAmount`) y la estampa de tiempo oficial (`Instant.now()`).

### 6. El Candado de Seguridad Doble (Optimistic Locking & Atomic SQL)
* **`@Version` en JPA:** Habilita bloqueo optimista a nivel de entidad. Si dos transacciones intentan confirmar un producto con la misma versión, una de ellas fallará con `OptimisticLockException`.
* **Resta Condicional en SQL:** En el repositorio se ejecuta:
  ```sql
  UPDATE flash_sale_products 
  SET available_stock = available_stock - :quantity 
  WHERE id = :id AND available_stock >= :quantity
  ```
  La cláusula `available_stock >= :quantity` garantiza a nivel de motor SQL que una fila solo se actualiza si hay stock suficiente, impidiendo inventarios negativos.

### 7. Regla Anti-Acaparadores (Anti-Scalping)
* En ventas flash, cada usuario tiene derecho a comprar **máximo 1 unidad**. El repositorio valida `existsByUserIdAndFlashSaleProductId(...)` antes de permitir la reserva, frenando bots y revendedores.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología | Propósito |
|---|---|---|
| **Lenguaje** | Java 21 | Plataforma base moderna (LTS) con Virtual Threads y mejoras de rendimiento |
| **Framework** | Spring Boot 4.1.2 | Inyección de dependencias, MVC, JPA, AMQP y Data Redis |
| **Persistencia Relacional** | PostgreSQL 16 | Fuente de la verdad transaccional para productos, eventos y órdenes |
| **Control de Base de Datos** | Flyway | Migraciones versionadas y reproducibles del esquema (`V1`, `V2`) |
| **Caché en Memoria** | Redis 7 + Lua Scripts | Gestión atómica de stock en RAM en submilisegundos |
| **Message Broker** | RabbitMQ 3 (Management) | Cola de mensajería asíncrona para desacoplamiento y absorción de picos |
| **Aislamiento** | Docker & Docker Compose | Contenedores reproducibles para Postgres, Redis y RabbitMQ |
| **Documentación API** | SpringDoc OpenAPI (Swagger UI) | Interfaz interactiva para probar endpoints en `/swagger-ui.html` |
| **Utilidades** | Lombok | Reducción de código repetitivo (Getters, Setters, Builders) |

---

## 🚀 Guía de Instalación y Ejecución

### 1. Prerrequisitos
* **Java 21** instalado y configurado en el PATH.
* **Docker Desktop** instalado y en ejecución.

### 2. Levantar la Infraestructura
En la raíz del proyecto, ejecuta:
```bash
docker compose up -d
```
Verifica que los 3 contenedores estén arriba:
```bash
docker compose ps
```
* PostgreSQL: `localhost:5432`
* Redis: `localhost:6379`
* RabbitMQ: `localhost:5672` (AMQP) y `localhost:15672` (Panel Web con usuario `guest` y clave `guest`).

### 3. Compilar y Arrancar la Aplicación
Ejecuta con el Gradle Wrapper:
```bash
./gradlew bootRun
```
Flyway detectará automáticamente las migraciones `V1__create_initial_schema.sql` y `V2__insert_sample_data.sql`, creando las tablas e insertando un producto de prueba (PlayStation 5 Slim con 10 unidades de stock).

La aplicación estará lista cuando la consola indique:
`Started FlashsaleApplication in X.XXX seconds`.

---

## 🧪 Pruebas Paso a Paso con Swagger UI

Abre en tu navegador:
👉 **http://localhost:8080/swagger-ui.html**

### Prueba 1: Pre-calentar el stock en Redis
1. Despliega `POST /api/flash-sales/preheat/{flashSaleProductId}`.
2. Ingresa `1` como ID del producto.
3. Haz clic en **Execute**.
4. **Respuesta:** `200 OK` $\rightarrow$ Las 10 unidades de la base de datos fueron cargadas a la memoria RAM de Redis bajo la clave `flash_sale:stock:1`.

### Prueba 2: Compra de Alta Concurrencia Exitosa
1. Despliega `POST /api/flash-sales/order`.
2. Envía el siguiente cuerpo JSON:
   ```json
   {
     "userId": 100,
     "flashSaleProductId": 1,
     "quantity": 1
   }
   ```
3. Haz clic en **Execute**.
4. **Respuesta:** `202 ACCEPTED` (La API respondió en menos de 10 ms).
5. En la terminal se observa el flujo completo:
   * Redis descuenta atómicamente el stock (quedan 9).
   * El mensaje viaja por el tubo de RabbitMQ.
   * El consumidor `OrderConsumer` toma el mensaje y registra la orden confirmada en PostgreSQL.

### Prueba 3: Validación Anti-Acaparadores (Anti-Bot)
1. Con el mismo `userId: 100`, haz clic en **Execute** nuevamente.
2. **Respuesta:** Excepción controlada: `"Ya has comprado este producto en esta oferta flash. Límite: 1 por persona."`. La petición es rechazada sin tocar Redis ni RabbitMQ.

### Prueba 4: Agotamiento de Stock en Tiempo Real
1. Cambia `userId` a `101` y compra las `9` unidades restantes (`quantity: 9`).
2. Intenta realizar una compra con `userId: 102` por `1` unidad.
3. **Respuesta:** `"Lo sentimos, ¡el producto está totalmente agotado!"`.
4. El script Lua de Redis intercepta la petición en 0.1 ms en memoria RAM, protegiendo a la base de datos de saturación.

---

## 📂 Estructura del Proyecto

```
src/main/
├── java/com/example/flashsale/
│   ├── FlashsaleApplication.java
│   ├── config/
│   │   ├── RabbitMQConfig.java         # Declaración de Exchange, Queue, Binding y Jackson Converter
│   │   └── RedisConfig.java            # Configuración de RedisTemplate y precarga del Script de Lua
│   ├── controller/
│   │   └── FlashSaleController.java    # Endpoints REST para preheat y order (Swagger)
│   ├── dto/
│   │   ├── FlashSaleRequest.java       # DTO de entrada validado desde cliente
│   │   └── OrderMessage.java           # Payload transferido por RabbitMQ
│   ├── entity/
│   │   ├── FlashSaleEvent.java         # Entidad de ventana de tiempo y estado del evento
│   │   ├── FlashSaleProduct.java       # Producto en oferta con stock y @Version
│   │   ├── Order.java                  # Pedido con estado, cliente y monto
│   │   └── Product.java                # Catálogo general de productos
│   ├── enums/
│   │   ├── EventStatus.java            # SCHEDULED, ACTIVE, FINISHED, CANCELLED
│   │   └── OrderStatus.java            # PENDING, CONFIRMED, FAILED, CANCELLED
│   ├── repository/
│   │   ├── FlashSaleEventRepository.java   # Búsqueda de eventos activos por instante
│   │   ├── FlashSaleProductRepository.java # Disminución atómica de stock en SQL
│   │   ├── OrderRepository.java            # Historial y control anti-duplicados por usuario
│   │   └── ProductRepository.java          # CRUD básico de productos
│   └── service/
│       ├── FlashSaleService.java       # Orquestador principal (Pre-calentamiento, Lua en Redis, RabbitMQ)
│       └── OrderConsumer.java          # Consumidor asíncrono (@RabbitListener) y persistencia final
└── resources/
    ├── application.yaml                # Propiedades de conexión a Postgres, Redis y RabbitMQ
    ├── db/migration/
    │   ├── V1__create_initial_schema.sql # Esquema DDL con índices y constraints
    │   └── V2__insert_sample_data.sql    # Datos semilla de prueba
    └── scripts/
        └── stock_decrease.lua          # Script atómico Lua monohilo para Redis
```

---

## 📄 Licencia

Este proyecto se distribuye bajo la licencia MIT. Código limpio, modular y preparado para arquitecturas de microservicios de alto tráfico.

