# Status Checker Android (Kotlin)

Aplicacion Android en Kotlin que muestra dos imagenes tipo boton:

- Verde (`si`) cuando el endpoint responde `HTTP 200`.
- Roja (`no`) en cualquier otro caso.

Incluye un boton `refrescar` para volver a consultar el endpoint.

## Donde cambiar el endpoint

Edita la constante `URL` en:

- `app/src/main/java/com/example/statuschecker/MainActivity.kt`

```kotlin
object EndpointConfig {
	const val URL = "https://example.com/health"
}
```

## Estructura principal

- `app/src/main/java/com/example/statuschecker/MainActivity.kt`: logica de consulta y actualizacion UI.
- `app/src/main/res/layout/activity_main.xml`: pantalla con botones de estado y boton refrescar.

## Ejecucion

1. Abre el proyecto en Android Studio.
2. Espera a que sincronice Gradle.
3. Ejecuta la app en un emulador o dispositivo Android.