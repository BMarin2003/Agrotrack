---
title: Compilar y distribuir la app Android
description: Firmar el release, compilar el AAB/APK y distribuirlo sin pasar por Google Play Store.
sidebar:
  order: 2
---

## Confirmar la URL de destino

El build type `release` de `agrotrack-app/app/build.gradle.kts` ya apunta al dominio de referencia de este manual:

```kotlin
release {
    // ...
    buildConfigField("String", "API_BASE_URL", "\"https://api.agrotrack.corall.pe/api/\"")
    buildConfigField("String", "WS_BASE_URL",  "\"wss://api.agrotrack.corall.pe/ws/\"")
}
```

Si el backend se desplegó en ese mismo dominio (ver [Desplegar el backend](/despliegue/backend/)), no hace falta cambiar nada. Si se usó un dominio distinto, actualizar esas dos líneas con la URL real antes de compilar — ambas deben usar `https`/`wss`, nunca `http`/`ws`, en un build de release.

## Generar el keystore de release

Un keystore es el archivo que firma digitalmente la app — sin él, Android rechaza instalar el APK/AAB. Se genera una sola vez y se guarda en un lugar seguro (nunca en el repositorio):

```bash
keytool -genkeypair -v \
  -keystore agrotrack-release.keystore \
  -alias agrotrack \
  -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` pide una contraseña para el keystore y otra para la clave — guardar ambas junto con el archivo `.keystore` en un gestor de contraseñas o similar, no en el repositorio.

## Configurar la firma

`agrotrack-app/local.properties` ya existe en el proyecto (contiene `sdk.dir`) y ya está en `.gitignore` — es el lugar correcto para las credenciales del keystore, ya que nunca se comitea. Agregar estas líneas:

```properties
RELEASE_STORE_FILE=/ruta/absoluta/a/agrotrack-release.keystore
RELEASE_STORE_PASSWORD=<contraseña-del-keystore>
RELEASE_KEY_ALIAS=agrotrack
RELEASE_KEY_PASSWORD=<contraseña-de-la-clave>
```

Y en `agrotrack-app/app/build.gradle.kts`, agregar un bloque `signingConfigs` que lea esas propiedades y usarlo en `buildTypes.release`:

```kotlin
import java.util.Properties

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    // ...

    signingConfigs {
        create("release") {
            storeFile     = localProps["RELEASE_STORE_FILE"]?.let { file(it) }
            storePassword = localProps["RELEASE_STORE_PASSWORD"] as String?
            keyAlias      = localProps["RELEASE_KEY_ALIAS"] as String?
            keyPassword   = localProps["RELEASE_KEY_PASSWORD"] as String?
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... resto de la configuración de release ya existente
        }
    }
}
```

Este bloque se agrega en la copia local del proyecto de quien compila el release — no forma parte del repositorio, porque sin las propiedades reales en `local.properties` no tiene ningún efecto, y con ellas nunca debe versionarse.

## Compilar el release

```bash
cd agrotrack-app
./gradlew bundleRelease   # genera el AAB, recomendado
# o bien:
./gradlew assembleRelease # genera el APK directamente
```

El archivo resultante queda en `app/build/outputs/bundle/release/app-release.aab` (o `app/build/outputs/apk/release/app-release.apk`).

## Distribución

Sin Google Play Store: el APK/AAB firmado se distribuye directamente — copiado al dispositivo, por un canal interno (MDM), o por un enlace privado de descarga. El usuario necesita habilitar "Instalar apps de orígenes desconocidos" para esa fuente específica la primera vez.

Publicar en Google Play Store es una alternativa posible (permite actualizaciones automáticas y una distribución más simple a futuro), pero el proceso de Play Console queda fuera de esta guía.

## Limitación conocida — notificaciones push

Las notificaciones de alertas (incluida la de recuperación de sensor) solo llegan mientras la app está en primer o segundo plano — no hay integración con Firebase Cloud Messaging configurada (`agrotrack-app/app/build.gradle.kts` tiene el plugin de Google Services comentado con una nota `TODO`). El detalle completo de este comportamiento y su justificación está documentado en [Notificación de recuperación de sensor](/historias-de-usuario/notificacion-recuperacion-sensor/).
