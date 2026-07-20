---
title: Introducción
description: Qué es Agrotrack y cómo están organizados sus componentes.
---

Agrotrack es una plataforma de monitoreo de cadena de frío agrícola en tiempo real: gateways físicos instalados en cámaras frigoríficas recolectan lecturas de temperatura (y métricas asociadas) de sus sensores conectados y las transmiten por MQTT. Un backend centraliza esa ingesta, la persiste, evalúa reglas de alerta, y la expone tanto por REST (consultas históricas, reportes) como por WebSocket (telemetría en vivo) a una aplicación Android usada por operadores y técnicos de campo.

## Componentes

El sistema tiene tres componentes:

- **Broker MQTT** — capa de transporte entre los gateways físicos y el backend.
- **`agrotrack-back`** — Bun + ElysiaJS + Supabase/PostgreSQL. Único punto de ingesta MQTT, dueño de las reglas de negocio, expone REST + WebSocket.
- **`agrotrack-app`** — Kotlin + Jetpack Compose. Nunca habla MQTT directamente: consume la API REST/WS del backend.

## Flujo de extremo a extremo

Gateway físico → MQTT → `mqtt.service` → `rules.engine` (umbrales + anomalías) + `watchdog.service` (detección de sensores offline/recuperados) → alertas guardadas en base de datos → broadcast por WebSocket (`telemetry.ws`) → app Android (`WebSocketManager`) → caché local + interfaz reactiva (Dashboard, Alertas, Detalle de gateway).

## Roles de usuario

El sistema define 4 roles con acceso diferenciado: **Administrador** (acceso total), **Operador** (monitoreo, umbrales, reportes), **Técnico** (gestión completa de gateways/sensores/calibración) y **Auditor** (solo lectura). El detalle de cómo se aplica este control de acceso está en [Seguridad y control de acceso](/arquitectura/seguridad/).

## Cómo se organiza este manual

- **[Arquitectura](/arquitectura/backend/)** — stack técnico y justificación de cada decisión, tanto del backend como de la app Android.
- **[Pruebas](/pruebas/resultados/)** — cobertura de pruebas unitarias, de caja blanca y de caja negra, con resultados reales de ejecución.
- **[Historias de usuario](/historias-de-usuario/notificacion-recuperacion-sensor/)** — casos de estudio en profundidad de funcionalidades específicas.
