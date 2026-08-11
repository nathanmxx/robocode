package spar;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.HitRobotEvent;
import robocode.RobotDeathEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * BOT DE TREINO - modelo de ameaca, nao e o robo de competicao.
 *
 * Reproduz o que uma equipe adversaria declarou que vai usar: o sample.Walls do
 * proprio Robocode com a mira muito aprimorada.
 *
 * A combinacao e perigosa e vale entender por que. O movimento do Walls parece
 * ingenuo, mas patrulhar o perimetro e defensivamente forte em melee: encostado
 * na parede, so metade das direcoes tem inimigo, o robo nunca fica no meio do
 * fogo cruzado, e a velocidade constante em linha reta o mantem longe de quem
 * esta se matando no centro. Nos meus testes o Walls original termina em 2o ou
 * 3o lugar COM a mira horrivel que ele tem de fabrica.
 *
 * O que falta nele e exatamente o que a equipe consertou. Entao aqui o
 * movimento e o do Walls e o canhao e de verdade:
 *   - ondas com aprendizado de GuessFactor, segmentado por distancia e
 *     velocidade lateral
 *   - mira linear como padrao enquanto nao ha estatistica
 *   - potencia por distancia e por energia restante
 *
 * O ponto fraco que sobra: andar em linha reta a velocidade constante e o caso
 * mais facil possivel para um canhao estatistico. E disso que dependemos.
 */
public class WallsDodge extends AdvancedRobot {

    private static final int BINS = 23;
    private static final int CENTER = BINS / 2;
    private static final int SEGMENTS = 9;

    /** Aprendizado sobrevive as rodadas, como no nosso robo. */
    private static final Map<String, float[][]> STATS = new HashMap<String, float[][]>();

    private final List<Wave> waves = new ArrayList<Wave>();

    private String  lockName;
    private double  lockDistance = 9999;
    private double  enemyEnergy = 100;
    private double  lastLateral;
    private int     lateralDir = 1;

    /** Rumo travado em multiplo de 90 graus, ao longo da parede. */
    private double desiredHeading = -1;

    /** Sentido ao longo da parede: inverte a cada disparo detectado. */
    private int  driveDir = 1;
    private long lastFlip = -99;

    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        desiredHeading = snapToWall(getHeadingRadians());

        while (true) {
            patrol();
            if (lockName == null) setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            updateWaves(getTime());
            execute();
        }
    }

    // ------------------------------------------------------------- movimento

    /**
     * O movimento do Walls, fiel ao original: anda em linha reta colado na
     * parede e vira 90 graus ao alcancar a proxima.
     *
     * Duas coisas aqui sao o que dao forca a ele, e ambas eu errei na primeira
     * tentativa. Primeiro, o rumo fica travado em multiplos de 90 graus - cortar
     * a arena em diagonal joga o robo no meio do fogo cruzado, que e justamente
     * o que colar na parede evita. Segundo, a velocidade e sempre maxima: com
     * setAhead(distancia ate o alvo) ele desacelera ao chegar, e alvo lento e
     * alvo facil.
     */
    private void patrol() {
        double margin = 45;
        Point2D.Double me = new Point2D.Double(getX(), getY());

        if (desiredHeading < 0) desiredHeading = snapToWall(getHeadingRadians());

        Point2D.Double probe = project(me, desiredHeading, 70);
        boolean wallAhead = probe.x < margin || probe.y < margin
                || probe.x > getBattleFieldWidth()  - margin
                || probe.y > getBattleFieldHeight() - margin;
        if (wallAhead) {
            desiredHeading = Utils.normalAbsoluteAngle(desiredHeading + Math.PI / 2);
        }

        setTurnRightRadians(Utils.normalRelativeAngle(desiredHeading - getHeadingRadians()));
        // A diferenca para o WallsPro: anda de re quando acabou de detectar um
        // disparo. O rumo continua travado na parede, entao ele nao perde a
        // protecao do perimetro - so inverte o sentido, que e o que invalida a
        // predicao que o adversario acabou de fazer.
        setAhead(driveDir * 300);
        setMaxVelocity(8);
    }

    /** Arredonda o rumo para o multiplo de 90 graus mais proximo. */
    private static double snapToWall(double heading) {
        return Utils.normalAbsoluteAngle(Math.round(heading / (Math.PI / 2)) * (Math.PI / 2));
    }

    public void onHitRobot(HitRobotEvent e) {
        desiredHeading = Utils.normalAbsoluteAngle(desiredHeading + Math.PI / 2);
    }

    // ------------------------------------------------------------------ mira

    public void onScannedRobot(ScannedRobotEvent e) {
        if (lockName != null && !e.getName().equals(lockName)
                && e.getDistance() > lockDistance + 80) return;

        lockName = e.getName();
        lockDistance = e.getDistance();

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        Point2D.Double me = new Point2D.Double(getX(), getY());
        Point2D.Double him = project(me, absBearing, e.getDistance());

        if (getOthers() == 1) {
            setTurnRadarRightRadians(
                    Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);
        }

        double lateral = e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing);
        if (Math.abs(lateral) > 0.15) lateralDir = lateral < 0 ? -1 : 1;
        lastLateral = lateral;

        // Queda de energia entre 0.1 e 3 e disparo. Inverter nesse instante
        // desmonta a mira que ele acabou de calcular.
        double drop = enemyEnergy - e.getEnergy();
        enemyEnergy = e.getEnergy();
        if (drop > 0.0999 && drop <= 3.0001 && getTime() - lastFlip > 4) {
            driveDir = -driveDir;
            lastFlip = getTime();
        }

        double power = choosePower(e.getDistance());
        if (power <= 0) return;

        double speed = 20 - 3 * power;
        double escape = Math.asin(8.0 / speed);
        int segment = segmentOf(e.getDistance(), lateral);

        double aim = statisticalAim(e.getName(), segment, absBearing, escape);
        if (aim == Double.MIN_VALUE) {
            aim = linearAim(me, him, e, speed);         // padrao enquanto nao ha dados
        }

        double turn = Utils.normalRelativeAngle(aim - getGunHeadingRadians());
        setTurnGunRightRadians(turn);

        if (Math.abs(turn) < Math.atan(18.0 / Math.max(e.getDistance(), 60))
                && getGunHeat() == 0 && getEnergy() > power + 0.2) {
            if (setFireBullet(power) != null) {
                Wave w = new Wave();
                w.origin = me;
                w.fireTime = getTime();
                w.speed = speed;
                w.direct = absBearing;
                w.escape = escape;
                w.lateral = lateralDir;
                w.segment = segment;
                w.name = e.getName();
                waves.add(w);
            }
        }
    }

    private double choosePower(double distance) {
        double power = distance < 200 ? 3.0 : (distance < 450 ? 2.4 : 1.8);
        power = Math.min(power, getEnergy() / 5);
        power = Math.min(power, enemyEnergy / 4 + 0.1);
        return power < 0.1 ? 0 : Math.min(power, 3.0);
    }

    private double linearAim(Point2D.Double me, Point2D.Double him, ScannedRobotEvent e, double speed) {
        double x = him.x, y = him.y;
        for (int t = 1; t <= 100; t++) {
            if (Math.hypot(x - me.x, y - me.y) <= speed * t) break;
            x += Math.sin(e.getHeadingRadians()) * e.getVelocity();
            y += Math.cos(e.getHeadingRadians()) * e.getVelocity();
            x = Math.max(18, Math.min(getBattleFieldWidth() - 18, x));
            y = Math.max(18, Math.min(getBattleFieldHeight() - 18, y));
        }
        return Math.atan2(x - me.x, y - me.y);
    }

    private double statisticalAim(String name, int segment, double direct, double escape) {
        float[][] s = STATS.get(name);
        if (s == null) return Double.MIN_VALUE;

        float[] bins = s[segment];
        double total = 0;
        for (int i = 0; i < BINS; i++) total += bins[i];
        if (total < 5) {
            bins = s[SEGMENTS];                        // global
            total = 0;
            for (int i = 0; i < BINS; i++) total += bins[i];
            if (total < 3) return Double.MIN_VALUE;
        }

        int best = CENTER;
        for (int i = 0; i < BINS; i++) if (bins[i] > bins[best]) best = i;
        return direct + ((best - CENTER) / (double) CENTER) * escape * lateralDir;
    }

    private int segmentOf(double distance, double lateral) {
        int d = distance < 250 ? 0 : (distance < 550 ? 1 : 2);
        double l = Math.abs(lateral);
        int v = l < 2 ? 0 : (l < 5.5 ? 1 : 2);
        return d * 3 + v;
    }

    // --------------------------------------------------------- aprender ondas

    private void updateWaves(long now) {
        for (Iterator<Wave> it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            if (now - w.fireTime > 120) { it.remove(); continue; }
        }
    }

    /** O acerto confirma onde o alvo estava: e o dado que treina o histograma. */
    public void onBulletHit(BulletHitEvent e) {
        Point2D.Double hit = new Point2D.Double(e.getBullet().getX(), e.getBullet().getY());

        Wave matched = null;
        double bestError = Double.MAX_VALUE;
        for (int i = 0; i < waves.size(); i++) {
            Wave w = waves.get(i);
            if (!w.name.equals(e.getName())) continue;
            double error = Math.abs(w.origin.distance(hit) - w.speed * (getTime() - w.fireTime));
            if (error < bestError) { bestError = error; matched = w; }
        }
        if (matched == null || bestError > 70) return;

        double offset = Utils.normalRelativeAngle(
                Math.atan2(hit.x - matched.origin.x, hit.y - matched.origin.y) - matched.direct);
        double gf = Math.max(-1, Math.min(1, offset / matched.escape * matched.lateral));
        int bin = (int) Math.round(gf * CENTER) + CENTER;
        bin = Math.max(0, Math.min(BINS - 1, bin));

        float[][] s = STATS.get(e.getName());
        if (s == null) { s = new float[SEGMENTS + 1][BINS]; STATS.put(e.getName(), s); }
        for (int i = 0; i < BINS; i++) {
            float weight = (float) (1.0 / (1.0 + (i - bin) * (i - bin)));
            s[matched.segment][i] += weight;
            s[SEGMENTS][i] += weight;
        }
        waves.remove(matched);
    }

    public void onRobotDeath(RobotDeathEvent e) {
        if (e.getName().equals(lockName)) { lockName = null; lockDistance = 9999; }
    }

    private static Point2D.Double project(Point2D.Double from, double angle, double distance) {
        return new Point2D.Double(from.x + Math.sin(angle) * distance,
                                  from.y + Math.cos(angle) * distance);
    }

    private static class Wave {
        Point2D.Double origin;
        long   fireTime;
        double speed, direct, escape;
        int    lateral, segment;
        String name;
    }
}
