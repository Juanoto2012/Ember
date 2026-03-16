# Changelog - Ember Browser

Todos los cambios notables en este proyecto serán documentados en este archivo.

## [1.2.0] - 2024-03-20
### Añadido
- **EmberTTS Bridge:** Implementación de un puente JavaScript personalizado para soportar la API de `speechSynthesis` en sitios web que no la detectan de forma nativa.
- **MediaSession Integration:** Nueva capacidad para controlar la reproducción de audio y video desde la barra de notificaciones y pantalla de bloqueo.
- **Filtro de Anuncios Progresivo:** Bloqueo inicial de dominios conocidos de publicidad (`doubleclick.net`, `googleadservices.com`, `googlesyndication.com`) para acelerar la carga de páginas.
- **Enhanced Protection (SSL):** Opción para forzar la cancelación de cargas en sitios con certificados SSL inválidos o caducados.
- **Pull-to-Refresh:** Integración nativa de Material3 para recargar páginas deslizando hacia abajo.

### Mejorado
- **Motor de Renderizado:** Optimización de `WebSettings` para mejorar la compatibilidad con sitios complejos como YouTube y Ventarys.
- **Modo PC (Desktop):** Refinado el cambio de User-Agent para forzar versiones de escritorio de manera más agresiva y persistente.
- **Gestión de Zoom:** Implementación de `TEXT_AUTOSIZING` y escala inicial dinámica para mejorar la legibilidad en pantallas pequeñas.

### Corregido
- Solucionado problema donde los videos en pantalla completa no se mostraban correctamente (ahora usa `CustomView` nativo).
- Corregida la persistencia de cookies de terceros para mejorar el inicio de sesión en sitios externos.

---

## [1.1.0] - 2024-02-15
### Añadido
- Soporte para descargas externas mediante un `DownloadListener` dedicado.
- Menú contextual en imágenes (Click largo) para acciones rápidas.
- Soporte para geolocalización web con solicitud de permisos dinámica.

### Mejorado
- Implementación de Jetpack Compose para toda la interfaz de usuario del navegador.
- Gestión de navegación mediante el botón físico de "Atrás".

---

## [1.0.0] - 2024-01-10
### Añadido
- Versión inicial estable de Ember Browser.
- Navegación básica mediante WebView.
- Soporte para JavaScript y almacenamiento DOM local.
- Barra de búsqueda integrada y soporte para URLs directas.
