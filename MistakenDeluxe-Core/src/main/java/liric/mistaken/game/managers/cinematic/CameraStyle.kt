package liric.mistaken.game.managers.cinematic

enum class CameraStyle {
    /**
     * El clásico: orbita alrededor del asesino cambiando el radio y la altura.
     */
    ORBIT_ZOOM_IN,

    /**
     * Empieza a ras del suelo y sube lentamente hasta la cara, para luego orbitar.
     */
    PAN_UP_REVEAL,

    /**
     * Empieza lejos, se acerca lentamente y de repente hace un acercamiento súbito (jumpscare) al rostro.
     */
    JUMPSCARE_RUSH,

    /**
     * Empieza muy alto y cae en espiral rápida hacia el objetivo.
     */
    DRONE_SPIRAL,

    /**
     * Movimiento errático que se teletransporta (frente, izquierda, espalda) simulando una cinta dañada.
     */
    ZIG_ZAG_GLITCH
}
