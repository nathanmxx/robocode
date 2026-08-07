package nx;

import robocode.ScannedRobotEvent;

import java.awt.geom.Point2D;

/**
 * Tudo que sabemos sobre um inimigo.
 *
 * Uma instancia sobrevive a batalha inteira (nao so a rodada), porque as
 * estatisticas de mira e de esquiva so ficam boas depois de algumas centenas de
 * tiros observados. O que e por rodada e zerado em {@link #newRound()}.
 */
public class Enemy {

    public final String name;

    public boolean alive;
    public long     lastSeen = -1;

    public Point2D.Double pos = new Point2D.Double();
    public double energy;
    public double heading;
    public double velocity;
    public double distance;

    /** Angulo absoluto de nos ate ele no ultimo scan. */
    public double absBearing;

    /** Velocidade de giro observada, em radianos por turno. Base da mira circular. */
    public double turnRate;

    /**
     * Sentido em que ele orbita a nossa volta: +1 anti-horario, -1 horario.
     * Mantem o ultimo valor nao nulo, porque quando ele para a velocidade
     * lateral zera mas a tendencia de movimento continua sendo informativa.
     */
    public int lateralDirection = 1;

    /** Velocidade perpendicular a linha que nos une. */
    public double lateralVelocity;

    // ------------------------------------------------------------ aprendizado
    // Nada disso e limpo entre rodadas: sao exatamente as estatisticas que fazem
    // o robo mirar melhor na rodada 5 do que na rodada 1.

    /** Histograma de GuessFactor por segmento: onde este inimigo costuma estar. */
    public final float[][] gfSegments = new float[Gun.SEGMENTS][Gun.BINS];

    /** Mesmo histograma sem segmentar, usado quando o segmento tem poucos dados. */
    public final float[] gfGlobal = new float[Gun.BINS];

    /** Placar dos canhoes virtuais contra este inimigo especifico. */
    public final int[] virtualHits  = new int[Gun.GUN_COUNT];
    public final int[] virtualShots = new int[Gun.GUN_COUNT];

    private double previousEnergy = 100.0;
    private double previousHeading;
    private long   previousScanTime = -1;

    /**
     * Potencia do tiro que ele acabou de disparar neste turno, ou 0.
     * Detectado pela queda de energia entre dois scans.
     */
    public double justFiredPower;

    public Enemy(String name) {
        this.name = name;
        this.alive = true;
    }

    public void newRound() {
        alive = true;
        lastSeen = -1;
        previousScanTime = -1;
        previousEnergy = 100.0;
        energy = 100.0;
        justFiredPower = 0;
        turnRate = 0;
        lateralVelocity = 0;
    }

    public void update(ScannedRobotEvent e, Leviathan self) {
        long now = e.getTime();

        absBearing = Util.absolute(self.getHeadingRadians() + e.getBearingRadians());
        distance   = e.getDistance();
        pos        = Util.project(self.myPosition(), absBearing, distance);

        double newHeading = e.getHeadingRadians();
        if (previousScanTime >= 0 && now > previousScanTime) {
            turnRate = Util.relative(newHeading - previousHeading) / (now - previousScanTime);
        }
        previousHeading  = newHeading;
        previousScanTime = now;

        heading  = newHeading;
        velocity = e.getVelocity();
        energy   = e.getEnergy();
        lastSeen = now;
        alive    = true;

        lateralVelocity = velocity * Math.sin(heading - absBearing);
        if (Math.abs(lateralVelocity) > 0.15) {
            lateralDirection = Util.sign(lateralVelocity);
        }
    }

    /**
     * Detecta disparo pela queda de energia. Precisa rodar uma vez por turno
     * para cada inimigo visivel, depois que os danos que nos causamos a ele ja
     * foram descontados via {@link #absorbKnownEnergyLoss(double)}.
     *
     * Nao e infalivel: bater na parede ou raspar em outro robo tambem tira
     * energia. Trata-se de aceitar algum ruido em troca de saber, quase sempre,
     * o instante exato em que um projetil nasceu.
     */
    public void detectFiring() {
        double drop = previousEnergy - energy;
        previousEnergy = energy;

        justFiredPower = (drop > 0.0999 && drop < 3.0001) ? drop : 0.0;
    }

    /**
     * Corrige a contabilidade com um valor exato de energia vindo do jogo
     * (por exemplo o {@code BulletHitEvent} de um projetil nosso que acertou).
     * Sem isso o dano que causamos seria lido como disparo dele.
     */
    public void syncEnergy(double exact) {
        previousEnergy = exact;
        energy = exact;
    }

    /** Ele recuperou energia ao acertar alguem; contabiliza para nao virar "tiro negativo". */
    public void absorbKnownEnergyGain(double amount) {
        previousEnergy += amount;
    }

    public boolean isFresh(long now) {
        return alive && lastSeen >= 0 && (now - lastSeen) <= 8;
    }

    /** Posicao extrapolada, para quando o scan esta velho. */
    public Point2D.Double predictedPosition(long now) {
        if (lastSeen < 0) return pos;
        long dt = Math.min(now - lastSeen, 20);
        return Util.project(pos, heading, velocity * dt);
    }
}
