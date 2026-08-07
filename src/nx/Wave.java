package nx;

import java.awt.geom.Point2D;

/**
 * Uma onda de tiro.
 *
 * Um projetil e invisivel para o adversario, mas a frente circular que ele
 * descreve nao e: sabendo de onde saiu, quando saiu e com que potencia, da para
 * calcular exatamente onde essa frente esta agora. Trabalhar com ondas em vez de
 * projeteis e o que permite aprender com cada tiro (nosso ou do inimigo) mesmo
 * sem enxergar a bala.
 */
public class Wave {

    public Point2D.Double origin;
    public long   fireTime;
    public double power;
    public double speed;

    /** Nome do robo alvo (nossas ondas) ou do atirador (ondas inimigas). */
    public String other;

    /** Angulo origem -> alvo no instante do disparo. E o "zero" do GuessFactor. */
    public double directAngle;

    /** Maior desvio angular alcancavel pelo alvo. Escala do GuessFactor. */
    public double escapeAngle;

    /** Sentido de orbita do alvo no disparo: define qual lado e o GF positivo. */
    public int lateralDirection;

    /** Combinacao de distancia e velocidade lateral no disparo. */
    public int segment;

    /**
     * GuessFactor que uma mira linear teria escolhido contra o alvo no instante
     * do disparo. Serve de palpite inicial de perigo enquanto nao ha estatistica
     * real sobre este atirador.
     */
    public double priorGuessFactor;

    /** Angulo absoluto que cada canhao virtual teria escolhido. */
    public double[] gunAngles;

    public boolean processed;

    public double radius(long now) {
        return (now - fireTime) * speed;
    }

    /** GuessFactor correspondente a um ponto, em [-1, 1]. */
    public double guessFactor(Point2D point) {
        double offset = Util.relative(Util.angle(origin, point) - directAngle);
        return Util.clamp(-1, offset / escapeAngle * lateralDirection, 1);
    }

    /** Angulo absoluto de disparo que corresponde a um GuessFactor. */
    public double angleFor(double guessFactor) {
        return directAngle + guessFactor * escapeAngle * lateralDirection;
    }
}
