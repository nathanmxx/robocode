package tcn;

import java.awt.geom.Point2D;

/**
 * Geometria e constantes fisicas do Robocode.
 *
 * O Robocode usa angulos em sentido horario a partir do Norte, e nao o sentido
 * anti-horario a partir do Leste da trigonometria comum. Por isso seno e cosseno
 * aparecem trocados em relacao ao habitual: x usa sin, y usa cos.
 */
public final class Util {

    public static final double TWO_PI  = Math.PI * 2;
    public static final double HALF_PI = Math.PI / 2;

    public static final double MAX_VELOCITY = 8.0;
    public static final double ROBOT_SIZE   = 36.0;

    private Util() {}

    /** Normaliza para o intervalo [-PI, PI). Use para "quanto girar". */
    public static double relative(double angle) {
        double a = angle % TWO_PI;
        if (a >= Math.PI)  a -= TWO_PI;
        if (a < -Math.PI)  a += TWO_PI;
        return a;
    }

    /** Normaliza para [0, 2PI). Use para "que direcao e essa". */
    public static double absolute(double angle) {
        double a = angle % TWO_PI;
        return a < 0 ? a + TWO_PI : a;
    }

    public static double angle(Point2D from, Point2D to) {
        return Math.atan2(to.getX() - from.getX(), to.getY() - from.getY());
    }

    public static Point2D.Double project(Point2D from, double angle, double distance) {
        return new Point2D.Double(from.getX() + Math.sin(angle) * distance,
                                  from.getY() + Math.cos(angle) * distance);
    }

    public static double clamp(double min, double value, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static double bulletSpeed(double power) {
        return 20.0 - 3.0 * power;
    }

    public static double bulletDamage(double power) {
        return power <= 1.0 ? 4.0 * power : 4.0 * power + 2.0 * (power - 1.0);
    }

    /** Energia devolvida ao atirador quando o projetil acerta. */
    public static double bulletReward(double power) {
        return 3.0 * power;
    }

    public static double gunHeat(double power) {
        return 1.0 + power / 5.0;
    }

    /**
     * Maior desvio angular que um alvo consegue obter contra um projetil desta
     * velocidade. Define a escala do GuessFactor: -1 e o extremo de fuga em um
     * sentido, +1 no outro.
     */
    public static double maxEscapeAngle(double bulletSpeed) {
        return Math.asin(MAX_VELOCITY / bulletSpeed);
    }

    /** Sinal, tratando zero como positivo (util para direcao lateral). */
    public static int sign(double v) {
        return v < 0 ? -1 : 1;
    }
}
