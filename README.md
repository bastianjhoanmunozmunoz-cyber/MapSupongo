# Rumbo · Bitácora de lugares

Aplicación Android en Java para registrar lugares sobre Google Maps, consultar la ubicación actual del dispositivo y guardar fotografías asociadas a coordenadas.

## Funcionalidades

- Mapa interactivo de Google Maps.
- Solicitud en tiempo de ejecución de permisos de ubicación precisa o aproximada.
- Botón para consultar y centrar el mapa en la ubicación actual mediante Fused Location Provider.
- Marcadores al tocar el mapa, con coordenadas y guardado local entre sesiones.
- Captura de fotografías con la cámara del dispositivo y asociación al lugar seleccionado.
- Lectura y escritura del archivo local de la bitácora mediante un `ExecutorService` de un solo hilo, para no bloquear la interfaz.

## Requisitos

- Android Studio y Android SDK 37 (el proyecto ya está configurado con ese nivel).
- JDK 11 o el JDK integrado de Android Studio.
- Una clave propia de Google Maps para Android.
- Emulador con Google Play Services o un dispositivo Android con ubicación y cámara.

## Configuración de Google Maps

1. Crea un proyecto en Google Cloud, activa **Maps SDK for Android** y genera una clave de API.
2. Restringe la clave a aplicaciones Android con el nombre de paquete `com.example.mapgps` y la huella SHA-1 de depuración de tu instalación.
3. Guarda la clave en `secrets.properties`, en la raíz del proyecto, junto a `settings.gradle.kts`:

   ```properties
   MAPS_API_KEY=PEGA_AQUI_TU_CLAVE
   ```

4. Este archivo ya está excluido de Git. Sincroniza Gradle y ejecuta la app. Si falta, Android Studio compilará usando `YOUR_API_KEY`, pero Google Maps no mostrará el mapa correctamente.

Para obtener la huella SHA-1 de depuración, abre la ventana Gradle de Android Studio, ejecuta `app > Tasks > android > signingReport` y copia la huella de la variante `debug`.

## Ejecutar

1. Abre esta carpeta en Android Studio.
2. Espera la sincronización de Gradle.
3. Configura `MAPS_API_KEY` en `secrets.properties` como se indica arriba.
4. Ejecuta la configuración `app` en un dispositivo o emulador con Google Play Services.
5. Acepta el permiso de ubicación para centrar el mapa. Toca el mapa para guardar marcadores y toca **Foto** para capturar una imagen del lugar seleccionado.

Los marcadores se guardan en el almacenamiento privado de la aplicación (`files/rumbo_places.json`). Las fotos se conservan en `files/photos/`, su referencia se guarda con las coordenadas y la última foto se muestra al volver a abrir la app.

## Crear y subir el repositorio solicitado

En GitHub crea un repositorio vacío llamado **SimpleGPSMapApp**. Luego, desde la raíz de este proyecto, configura el remoto con la URL de tu repositorio y publica la rama principal:

```bash
git init
git add .
git commit -m "Crear bitácora GPS Rumbo"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/SimpleGPSMapApp.git
git push -u origin main
```

El archivo local `secrets.properties` está excluido por `.gitignore`; no quites esa exclusión ni agregues la clave a `gradle.properties`, al manifiesto o al README.

## Respuestas de reflexión

1. **¿Qué aprendí?** A integrar Google Maps con la ubicación del teléfono, solicitar permisos durante la ejecución, añadir marcadores y evitar que la lectura o escritura local de la bitácora bloquee la interfaz usando un hilo de trabajo.
2. **¿Cómo se usa GPS en otras aplicaciones?** Se usa para navegación, seguimiento de entregas, transporte, búsqueda de servicios cercanos, actividad deportiva y geolocalización de fotografías.
3. **¿Qué dificultades pueden aparecer?** Configurar y restringir la clave de Maps, obtener permisos, probar ubicación real en un emulador y coordinar la captura de fotos con el ciclo de vida de Android. La aplicación muestra estados cuando se niega el permiso o no llega una ubicación.

## Estado de validación

La implementación está preparada para compilar en Android Studio. La visualización de Google Maps depende de configurar una clave válida; la ubicación real depende de los permisos, los servicios de ubicación y el dispositivo usado.
