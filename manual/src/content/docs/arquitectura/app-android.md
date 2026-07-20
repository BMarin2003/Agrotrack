---
title: Aplicación Android — stack y justificación
description: Arquitectura por capas, DI, networking y convenciones de agrotrack-app.
sidebar:
  order: 3
---

## Kotlin + Jetpack Compose

La UI está escrita 100% en Compose (`androidx.compose`, BOM `2024.12.01`), sin vistas XML. Justificación:

- **UI declarativa y unidireccional** — cada pantalla es una función `@Composable` que renderiza a partir de un `UiState` inmutable; no hay manipulación imperativa de vistas ni sincronización manual de estado con el layout.
- **Material 3** (`compose-material3`) como sistema de diseño — componentes (`FilterChip`, `TabRow`, `DateRangePicker`, `TimeInput`) cubren directamente necesidades del dominio (selección de rango de fechas para reportes, navegación por pestañas) sin tener que construir controles custom.
- **Menos código por pantalla** — no existen archivos `.xml` de layout ni `Fragment`/`Adapter` de `RecyclerView`; `LazyColumn`/`LazyRow` reemplazan esa capa directamente en Kotlin.

## Arquitectura en capas (Clean Architecture)

```
presentation/   # Composables + ViewModel + UiState, por feature
domain/         # modelos de dominio + interfaces de repositorio + casos de uso
data/           # implementación de repositorios + DTOs + fuentes remota/local
core/           # DI, seguridad, networking, notificaciones — transversal
```

- **`domain/`** no depende de Android ni de ninguna librería de red/BD — son clases Kotlin puras (`data class Gateway`, `data class SensorReading`) e interfaces (`interface TelemetryRepository`). Esto permite testear lógica de negocio sin instanciar nada de Android.
- **`data/`** implementa esas interfaces (`TelemetryRepositoryImpl`) combinando una fuente remota (Retrofit) y una local (Room), y es la única capa que conoce la forma exacta del JSON del backend (vía DTOs con `@SerializedName`) — un cambio en el JSON de la API se absorbe acá, sin propagarse a `domain/` ni a `presentation/`.
- **`presentation/`** solo conoce `domain/` — un `ViewModel` nunca importa un DTO ni Retrofit directamente.

Esta separación es lo que permite, por ejemplo, que el módulo de Reportes tenga tres niveles (Sensor/Gateway/General) reusando el mismo `TelemetryRepository` y los mismos modelos de dominio (`SensorReading`) para los tres, sin triplicar lógica de mapeo.

## Inyección de dependencias — Hilt

Hilt (`2.51.1`) se usa en vez de una solución manual (Service Locator) o Koin por:

- **Grafo de dependencias verificado en compile-time** — un `ViewModel` con una dependencia sin binding falla el build, no en runtime.
- **Integración nativa con `ViewModel`/`Composable`** (`@HiltViewModel`, `hiltViewModel()`) — cada pantalla obtiene su ViewModel ya resuelto sin código de fábrica manual.
- **Módulos por responsabilidad** (`core/di/AppModule.kt`, `NetworkModule.kt`, `DatabaseModule.kt`) — los `@Provides`/`@Binds` están agrupados por qué construyen (servicios Retrofit, la base de datos Room, los repositorios), no todos en un único archivo gigante.

## Networking — Retrofit + OkHttp + Gson

- **Retrofit** (`2.11.0`) define los endpoints como interfaces anotadas (`interface TelemetryApiService { @GET("reports/gateway/{gatewayId}") ... }`) — el contrato HTTP es un tipo Kotlin verificable en compile-time, no strings de URL sueltos.
- **OkHttp** como cliente subyacente, con interceptor de autenticación que adjunta el JWT a cada request y reconexión configurada para el `WebSocketManager`.
- **Gson** para (de)serialización — elegido sobre Moshi/kotlinx.serialization por integrarse directamente con el conversor oficial de Retrofit (`converter-gson`) y por ya ser la dependencia usada en todo el proyecto para el `WebSocketManager`.

## Persistencia local — Room

Room (`2.7.1`) cachea lecturas y alertas localmente (`data/local/`), lo que permite que el Dashboard muestre la última data conocida inmediatamente al abrir la app (antes de que resuelva la llamada de red) y tolere cortes de conectividad breves sin dejar la pantalla vacía.

## Estado reactivo — `StateFlow` sobre `LiveData`

Todos los ViewModels exponen `StateFlow<UiState>` (no `LiveData`). Motivo: `StateFlow` es parte de coroutines (ya la base de toda la capa de red/BD del proyecto), tiene un valor inicial obligatorio (evita el `null` inicial típico de `LiveData`), y se consume de forma idiomática en Compose vía `collectAsStateWithLifecycle()`, sin necesitar un puente `LiveData → Compose State`.

## Tiempo real — WebSocket

`WebSocketManager` (`core/network/WebSocketManager.kt`) es un singleton (`@Singleton`) sobre OkHttp que mantiene una única conexión WS por sesión de app, con reconexión exponencial (backoff hasta 30s, máximo 10 intentos) y *keepalive* por ping cada 25s. Su ciclo de vida está atado a la sesión del usuario (se conecta tras login/verificación de sesión válida, se desconecta en logout), no a una pantalla puntual — así las notificaciones de alertas siguen llegando mientras el usuario navega por la app, no solo mientras está parado en el Dashboard.

## Notificaciones locales

`AlertNotificationHelper` (`core/notification/`) traduce cada alerta recibida por WebSocket en una notificación real del sistema operativo (`NotificationCompat` + `NotificationManagerCompat`), con un canal (`NotificationChannel`) por tipo de alerta (umbral, sensor desconectado, dato anómalo, sensor recuperado) — cada canal es configurable por el usuario desde los ajustes del sistema operativo, siguiendo la guía de notificaciones de Android.

## Seguridad de sesión

`SessionManager` persiste el token JWT en `EncryptedSharedPreferences` (AES-256), no en `SharedPreferences` plano — el token nunca queda legible en disco. Al reabrir la app, la sesión se valida contra el backend (`verifyToken`) antes de asumirla vigente; un token expirado o revocado redirige a Login automáticamente.

## Convenciones de nomenclatura

| Elemento | Convención | Ejemplo |
|---|---|---|
| Pantalla | `<Feature>Screen` | `GatewayReportScreen` |
| ViewModel | `<Feature>ViewModel`, `@HiltViewModel` | `GatewayReportViewModel` |
| Estado de UI | `<Feature>UiState`, `data class` inmutable | `GatewayReportUiState` |
| Caso de uso | `<Verbo><Entidad>UseCase` | `GetLatestReadingsUseCase` |
| Repositorio (contrato) | `domain/repository/<Entidad>Repository` | `TelemetryRepository` |
| Repositorio (impl.) | `data/repository/<Entidad>RepositoryImpl` | `TelemetryRepositoryImpl` |
| DTO remoto | `<Entidad>Dto`, con `@SerializedName` snake_case→camelCase | `GatewayReportDto` |
| Modelo de dominio | sin sufijo, Kotlin puro | `Gateway`, `SensorReading` |

Esta simetría de sufijos (`...Screen`/`...ViewModel`/`...UiState` para la misma feature) permite ubicar cualquier archivo relacionado a una pantalla por convención de nombre, sin necesidad de un índice central.

## Resumen de decisiones

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| Jetpack Compose | Vistas XML + Fragments | UI declarativa, menos boilerplate por pantalla |
| Clean Architecture | MVVM sin separación domain/data | Aislar el modelo de negocio de los detalles de red/BD |
| Hilt | Koin / Service Locator manual | Grafo de dependencias verificado en compile-time |
| StateFlow | LiveData | Nativo de coroutines, sin valor `null` inicial, idiomático en Compose |
| WebSocket a nivel de sesión | WebSocket atado a la pantalla Dashboard | Notificaciones en tiempo real disponibles en toda la app, no solo en una pantalla |
