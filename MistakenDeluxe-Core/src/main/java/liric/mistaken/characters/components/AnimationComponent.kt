package liric.mistaken.characters.components

import liric.mistaken.characters.core.CharacterComponent

/**
 * Componente base para manejar la reproducción de animaciones.
 * Es independiente del estado; simplemente expone comandos para reproducir/detener.
 */
interface AnimationComponent : CharacterComponent {
    
    /**
     * Reproduce una animación.
     * 
     * @param animationName El nombre de la animación (ej. "walk", "attack").
     * @param speed Multiplicador de velocidad (1.0f = normal).
     * @param loop Si la animación debe repetirse (loop) o reproducirse una sola vez (play once).
     * @param priority Prioridad de la animación (útil si el engine subyacente soporta prioridades).
     * @param onComplete Callback opcional llamado cuando la animación termina (si loop es false).
     */
    fun play(
        animationName: String, 
        speed: Float = 1.0f, 
        loop: Boolean = false, 
        priority: Int = 0,
        onComplete: (() -> Unit)? = null
    )

    /**
     * Detiene una animación específica.
     */
    fun stop(animationName: String)

    /**
     * Reemplaza una animación en ejecución por otra.
     */
    fun replace(oldAnimation: String, newAnimation: String, speed: Float = 1.0f, loop: Boolean = false)

    /**
     * Detiene todas las animaciones del personaje.
     */
    fun stopAll()
}
