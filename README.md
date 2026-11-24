# MASTERS 3

Simulador de liga de fútbol para Android, escrito en **Kotlin** con **Jetpack Compose** y **Material 3**.

La idea: montar una liga con tus propios equipos y jugadores, simular el calendario completo, jugar los playoffs y llevar el historial de cada temporada con su campeón, sus goleadores y su mejor portero.

## Funcionalidad

- **Alta de equipos y plantillas**: nombre de equipo y jugadores con posición, capitán y portero.
- **Calendario**: generación de los enfrentamientos de la temporada y simulación partido a partido.
- **Clasificación**: puntos, goles a favor y en contra, ganados, empatados y perdidos.
- **Playoffs**: eliminatorias al terminar la liga regular.
- **Estadísticas**: máximos goleadores, asistentes y paradas, con capitán destacado.
- **Historial de temporadas**: campeón, subcampeón y los tres mejores jugadores de cada temporada.
- **Navegación** entre las seis pantallas (menú, alta, calendario, clasificación, estadísticas e historial).

## Stack

| Pieza | Versión |
|---|---|
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 8.13.0 |
| Gradle | 8.13 |
| Jetpack Compose (BOM) | 2024.09.00 |
| Material 3 | incluido en el BOM |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| JDK | 17 o superior |

## Cómo compilarlo

Requisitos: JDK 17 o superior y el SDK de Android con la plataforma 36 instalada.

```bash
git clone https://github.com/Virus34558/MASTERS3.git
cd MASTERS3
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. También se puede abrir el proyecto directamente en Android Studio y ejecutarlo con el botón de *Run*.

## Estructura

```
app/src/main/java/com/example/masters3/
├── MainActivity.kt        # toda la app: modelos, estado, simulacion y UI Compose
└── ui/theme/              # tema Material 3 (colores y tipografia)
```

## Estado y limitaciones

Proyecto **en desarrollo**, funcional y compilable. Dicho sin adornos:

- Toda la lógica vive en un único `MainActivity.kt` de unas 480 líneas. Funciona, pero está pendiente de separar en capas (modelo, estado, UI) y de mover los modelos a su propio paquete.
- **No hay persistencia**: los equipos y las temporadas se guardan en memoria y se pierden al cerrar la app.
- La simulación de resultados es aleatoria; no hay motor de probabilidades por calidad de plantilla.
- Cobertura de tests mínima: solo las plantillas que genera Android Studio.
- Hay un aviso de deprecación de `menuAnchor()` (línea 292) pendiente de actualizar a la sobrecarga con `MenuAnchorType`.

Los siguientes pasos, por orden: separar la arquitectura, añadir persistencia local con Room y sustituir el azar por un motor de simulación con pesos.

## Autor

Unai Sayago — [LinkedIn](https://www.linkedin.com/in/unai-sayago-sanz-199285248/) — unai.sayago@gmail.com
