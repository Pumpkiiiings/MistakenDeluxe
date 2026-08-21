package liric.mistaken.models.components

import liric.mistaken.models.core.CharacterComponent

/**
 * Componente base para manejar la reproducciÃ³n de animaciones.
 * Es independiente del estado; simplemente expone comandos para reproducir/detener.
 */
interface AnimationComponent : CharacterComponent {
    
    /**
     * Reproduce una animaciÃ³n.
     * 
     * @param animationName El nombre de la animaciÃ³n (ej. "walk", "attack").
     * @param speed Multiplicador de velocidad (1.0f = normal).
     * @param loop Si la animaciÃ³n debe repetirse (loop) o reproducirse una sola vez (play once).
     * @param priority Prioridad de la animaciÃ³n (Ãºtil si el engine subyacente soporta prioridades).
     * @param onComplete Callback opcional llamado cuando la animaciÃ³n termina (si loop es false).
     */
    fun play(
        animationName: String, 
        speed: Float = 1.0f, 
        loop: Boolean = false, 
        priority: Int = 0,
        onComplete: (() -> Unit)? = null
    )

    /**
     * Detiene una animaciÃ³n especÃ­fica.
     */
    fun stop(animationName: String)

    /**
     * Reemplaza una animaciÃ³n en ejecuciÃ³n por otra.
     */
    fun replace(oldAnimation: String, newAnimation: String, speed: Float = 1.0f, loop: Boolean = false)

    /**
     * Detiene todas las animaciones del personaje.
     */
    fun stopAll()
}
