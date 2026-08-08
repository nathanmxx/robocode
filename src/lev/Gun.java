package lev;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Canhao.
 *
 * Nenhuma mira unica ganha de todo mundo: a linear destroi quem anda reto e erra
 * feio em quem oscila; a circular pega quem orbita; a estatistica precisa de
 * dados para ficar boa. Entao o robo calcula as quatro miras a cada tiro, anota
 * qual delas TERIA acertado quando a onda chega no alvo, e passa a usar contra
 * cada inimigo a que de fato mais acerta naquele inimigo.
 *
 * Isso resolve o problema de o campeonato ter 15 adversarios diferentes: o robo
 * nao precisa de uma mira boa na media, precisa da mira certa para cada um.
 */
public class Gun {

    /** Impar de proposito: existe um bin exatamente no centro (tiro direto). */
    public static final int BINS   = 23;
    public static final int CENTER = BINS / 2;

    public static final int SEGMENTS = 9;   // 3 faixas de distancia x 3 de velocidade lateral

    public static final int HEAD_ON  = 0;
    public static final int LINEAR   = 1;
    public static final int CIRCULAR = 2;
    public static final int STATS    = 3;
    public static final int GUN_COUNT = 4;

    /** Abaixo disso o segmento nao tem dados suficientes e caimos no global. */
    private static final double SEGMENT_CONFIDENCE = 6.0;
    private static final double GLOBAL_CONFIDENCE  = 3.0;

    /** Tiros minimos antes de confiar na taxa de acerto de um canhao virtual. */
    private static final int MIN_SAMPLES = 10;

    private final Leviathan bot;
    private final List<Wave> waves = new ArrayList<Wave>();

    public Gun(Leviathan bot) {
        this.bot = bot;
    }

    public void newRound() {
        waves.clear();
    }

    // ------------------------------------------------------------- segmentacao

    public static int segmentOf(Enemy e) {
        int d = e.distance < 250 ? 0 : (e.distance < 550 ? 1 : 2);
        double lateral = Math.abs(e.lateralVelocity);
        int v = lateral < 2.0 ? 0 : (lateral < 5.5 ? 1 : 2);
        return d * 3 + v;
    }

    // ------------------------------------------------------------------ mirar

    /**
     * Calcula as quatro miras, escolhe a melhor para este inimigo, aponta e
     * dispara quando alinhado. Devolve a onda criada, ou null se nao atirou.
     */
    public Wave engage(Enemy target, double power, long now) {
        if (target == null || power <= 0) return null;

        double speed = Util.bulletSpeed(power);
        Point2D.Double me = bot.position();
        Point2D.Double enemyPos = target.predictedPosition(now);

        double directAngle = Util.angle(me, enemyPos);
        double escapeAngle = Util.maxEscapeAngle(speed);
        int    segment     = segmentOf(target);

        double[] angles = new double[GUN_COUNT];
        angles[HEAD_ON]  = directAngle;
        angles[LINEAR]   = predictAngle(me, target, speed, false);
        angles[CIRCULAR] = predictAngle(me, target, speed, true);
        angles[STATS]    = statisticalAngle(target, directAngle, escapeAngle, segment);

        int chosen = chooseGun(target);
        double aim = angles[chosen];

        double gunTurn = Util.relative(aim - bot.getGunHeadingRadians());
        bot.setTurnGunRightRadians(gunTurn);

        // Tolerancia proporcional ao tamanho angular do alvo: de longe o canhao
        // precisa estar bem mais alinhado do que de perto para o tiro valer.
        double tolerance = Math.atan(20.0 / Math.max(target.distance, 60));
        if (Math.abs(gunTurn) > tolerance) return null;
        if (bot.getGunHeat() > 0) return null;
        if (bot.getEnergy() <= power + 0.2) return null;

        robocode.Bullet fired = bot.setFireBullet(power);
        if (fired == null) return null;

        // O projetil em voo tambem e defesa: enquanto viaja, bloqueia o trecho
        // da onda inimiga que ele atravessa.
        bot.shield().register(fired);

        Wave w = new Wave();
        w.origin = new Point2D.Double(me.x, me.y);
        w.fireTime = now;
        w.power = power;
        w.speed = speed;
        w.other = target.name;
        w.directAngle = directAngle;
        w.escapeAngle = escapeAngle;
        w.lateralDirection = target.lateralDirection;
        w.segment = segment;
        w.gunAngles = angles;
        waves.add(w);
        return w;
    }

    /** Mira linear ou circular, convergindo o ponto de encontro por iteracao. */
    private double predictAngle(Point2D.Double me, Enemy target, double speed, boolean circular) {
        double x = target.pos.x, y = target.pos.y;
        double heading = target.heading;
        double turnRate = circular ? Util.clamp(-0.2, target.turnRate, 0.2) : 0.0;
        double velocity = target.velocity;

        java.awt.geom.Rectangle2D.Double f = bot.battleField();

        for (int t = 1; t <= 120; t++) {
            if (Math.hypot(x - me.x, y - me.y) <= speed * t) break;
            heading += turnRate;
            x += Math.sin(heading) * velocity;
            y += Math.cos(heading) * velocity;
            // Ele nao atravessa a parede; sem esse limite a predicao circular
            // manda o tiro para fora do campo.
            x = Util.clamp(f.getMinX(), x, f.getMaxX());
            y = Util.clamp(f.getMinY(), y, f.getMaxY());
        }
        return Util.angle(me, new Point2D.Double(x, y));
    }

    /** Mira estatistica: o bin mais visitado do segmento vira angulo de tiro. */
    private double statisticalAngle(Enemy target, double directAngle, double escapeAngle, int segment) {
        float[] stats = target.gfSegments[segment];
        double total = sum(stats);
        if (total < SEGMENT_CONFIDENCE) {
            stats = target.gfGlobal;
            total = sum(stats);
        }
        if (total < GLOBAL_CONFIDENCE) {
            return directAngle;               // ainda sem dados: tiro direto
        }

        int best = CENTER;
        for (int i = 0; i < BINS; i++) {
            if (stats[i] > stats[best]) best = i;
        }
        double gf = (best - CENTER) / (double) CENTER;
        return directAngle + gf * escapeAngle * target.lateralDirection;
    }

    /**
     * Escolhe o canhao com melhor taxa de acerto real contra este inimigo.
     * Sem amostras suficientes usa a mira linear, que e a aposta mais segura
     * contra um adversario desconhecido.
     */
    private int chooseGun(Enemy target) {
        int best = LINEAR;
        double bestRate = -1;
        for (int g = 0; g < GUN_COUNT; g++) {
            if (target.virtualShots[g] < MIN_SAMPLES) continue;
            double rate = target.virtualHits[g] / (double) target.virtualShots[g];
            if (rate > bestRate) {
                bestRate = rate;
                best = g;
            }
        }
        return best;
    }

    // -------------------------------------------------------- aprender por onda

    /**
     * Percorre as ondas em voo. Quando uma alcanca o alvo, registra em que
     * GuessFactor ele estava e marca quais canhoes virtuais teriam acertado.
     */
    public void update(long now) {
        for (Iterator<Wave> it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            Enemy target = Leviathan.KNOWN.get(w.other);

            if (target == null || !target.alive) { it.remove(); continue; }

            Point2D.Double where = target.predictedPosition(now);
            double distance = w.origin.distance(where);
            double radius = w.radius(now);

            if (radius < distance - Util.ROBOT_SIZE / 2) continue;   // ainda nao chegou
            if (radius > distance + 60) { it.remove(); continue; }   // ja passou reto

            record(target, w, where);
            it.remove();
        }

        // Ondas orfas (alvo morreu, rodada virou) nao ficam acumulando.
        while (waves.size() > 64) waves.remove(0);
    }

    private void record(Enemy target, Wave w, Point2D.Double where) {
        double gf = w.guessFactor(where);
        int bin = (int) Math.round(gf * CENTER) + CENTER;
        bin = (int) Util.clamp(0, bin, BINS - 1);

        // Suavizacao: o acerto reforca o bin exato e, cada vez menos, os
        // vizinhos. Com poucos tiros isso e o que faz a estatistica generalizar
        // em vez de decorar pontos isolados.
        for (int i = 0; i < BINS; i++) {
            float weight = (float) (1.0 / (1.0 + (i - bin) * (i - bin)));
            target.gfSegments[w.segment][i] += weight;
            target.gfGlobal[i] += weight;
        }

        // Qual canhao teria acertado: o alvo ocupa um arco angular visto da
        // origem, e basta o angulo proposto cair dentro dele.
        double actual = Util.angle(w.origin, where);
        double hitArc = Math.atan((Util.ROBOT_SIZE / 2) / Math.max(w.origin.distance(where), 40));
        for (int g = 0; g < GUN_COUNT; g++) {
            target.virtualShots[g]++;
            if (Math.abs(Util.relative(w.gunAngles[g] - actual)) < hitArc) {
                target.virtualHits[g]++;
            }
        }
    }

    private static double sum(float[] a) {
        double t = 0;
        for (int i = 0; i < a.length; i++) t += a[i];
        return t;
    }

    /** Nome do canhao em uso, para depuracao. */
    public String activeGunName(Enemy target) {
        if (target == null) return "-";
        switch (chooseGun(target)) {
            case HEAD_ON:  return "direto";
            case LINEAR:   return "linear";
            case CIRCULAR: return "circular";
            default:       return "estatistico";
        }
    }
}
