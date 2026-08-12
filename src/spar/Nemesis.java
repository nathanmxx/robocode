package spar;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.HitByBulletEvent;
import robocode.RobotDeathEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * BOT DE TREINO - regua, nao e o robo de competicao.
 *
 * MOTIVO DE EXISTIR: o TCN ganha de todos os adversarios do conjunto de teste,
 * e por isso parou de ser possivel medir se uma mudanca melhora ou piora. Sem
 * alguem que de trabalho, toda alteracao vira aposta. Este robo existe para ser
 * esse alguem.
 *
 * Ele foi escrito de proposito com a peca que falta no TCN: DESVIO DE TIRO EM
 * MELEE. O TCN so surfa ondas no duelo; nas fases de arena cheia ele evita
 * posicoes ruins mas nunca reage a um projetil especifico. Aqui as ondas
 * inimigas entram como mais um termo do campo de risco, entao cada ponto
 * candidato e julgado tambem por quantos tiros em voo passariam perto dele.
 *
 * O resto e o padrao do genero: campo de risco para escolher para onde ir,
 * canhao de GuessFactor segmentado, radar girando em melee e travado em duelo.
 */
public class Nemesis extends AdvancedRobot {

    private static final int BINS = 25;
    private static final int CENTER = BINS / 2;
    private static final int SEGMENTS = 9;

    /** Aprendizado atravessa as rodadas. */
    private static final Map<String, Foe> KNOWN = new HashMap<String, Foe>();

    private final List<EWave> incoming = new ArrayList<EWave>();
    private final List<MyWave> mine = new ArrayList<MyWave>();

    private Point2D.Double me = new Point2D.Double();
    private Rectangle2D.Double safe;
    private Point2D.Double destination;

    private Foe target;

    // ------------------------------------------------------------------ ciclo

    public void run() {
        setColors(new java.awt.Color(120, 20, 20), new java.awt.Color(220, 220, 220),
                  new java.awt.Color(220, 60, 60));
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        double inset = 40;
        safe = new Rectangle2D.Double(inset, inset,
                getBattleFieldWidth() - 2 * inset, getBattleFieldHeight() - 2 * inset);

        for (Foe f : KNOWN.values()) f.newRound();
        incoming.clear();
        mine.clear();
        destination = null;

        while (true) {
            me.setLocation(getX(), getY());
            long now = getTime();

            expireWaves(now);
            learnFromMyWaves(now);
            pickTarget(now);
            driveRadar(now);
            move(now);
            shoot(now);

            execute();
        }
    }

    // ---------------------------------------------------------------- eventos

    public void onScannedRobot(ScannedRobotEvent e) {
        Foe f = KNOWN.get(e.getName());
        if (f == null) { f = new Foe(e.getName()); KNOWN.put(e.getName(), f); }

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        Point2D.Double was = f.pos;
        double previousEnergy = f.energy;

        f.pos = project(new Point2D.Double(getX(), getY()), absBearing, e.getDistance());
        f.distance = e.getDistance();
        f.absBearing = absBearing;
        f.heading = e.getHeadingRadians();
        f.velocity = e.getVelocity();
        f.energy = e.getEnergy();
        f.lastSeen = e.getTime();
        f.alive = true;

        double lateral = f.velocity * Math.sin(f.heading - absBearing);
        if (Math.abs(lateral) > 0.15) f.lateralDir = lateral < 0 ? -1 : 1;
        f.lateral = lateral;

        // Disparo detectado pela queda de energia: nasce uma onda inimiga.
        double drop = previousEnergy - f.energy;
        if (drop > 0.0999 && drop <= 3.0001 && was != null) {
            EWave w = new EWave();
            w.origin = was;
            w.fireTime = e.getTime() - 1;
            w.speed = 20 - 3 * drop;
            w.shooter = f.name;
            w.direct = Math.atan2(getX() - was.x, getY() - was.y);
            w.escape = Math.asin(8.0 / w.speed);
            double ourLateral = getVelocity() * Math.sin(getHeadingRadians() - w.direct);
            w.lateralDir = ourLateral < 0 ? -1 : 1;
            incoming.add(w);
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        Foe f = KNOWN.get(e.getName());
        if (f != null) f.energy += 3 * e.getPower();

        // Anota em que GuessFactor ele acertou: e o mapa que orienta a esquiva.
        Point2D.Double at = new Point2D.Double(getX(), getY());
        EWave best = null; double bestError = Double.MAX_VALUE;
        for (int i = 0; i < incoming.size(); i++) {
            EWave w = incoming.get(i);
            if (!w.shooter.equals(e.getName())) continue;
            double error = Math.abs(w.origin.distance(at) - w.speed * (getTime() - w.fireTime));
            if (error < bestError) { bestError = error; best = w; }
        }
        if (best == null || bestError > 60 || f == null) return;

        int bin = binOf(gfOf(best, at));
        for (int i = 0; i < BINS; i++) {
            f.danger[i] += (float) (1.0 / (1.0 + (i - bin) * (i - bin)));
        }
        incoming.remove(best);
    }

    public void onBulletHit(BulletHitEvent e) {
        Foe f = KNOWN.get(e.getName());
        if (f != null) f.energy = e.getEnergy();
    }

    public void onRobotDeath(RobotDeathEvent e) {
        Foe f = KNOWN.get(e.getName());
        if (f != null) f.alive = false;
    }

    // ------------------------------------------------------------------- alvo

    private void pickTarget(long now) {
        Foe best = null; double bestScore = Double.MAX_VALUE;
        for (Foe f : KNOWN.values()) {
            if (!f.alive || f.lastSeen < 0) continue;
            double score = f.distance + (now - f.lastSeen) * 8;
            if (score < bestScore) { bestScore = score; best = f; }
        }
        target = best;
    }

    private List<Foe> living() {
        List<Foe> out = new ArrayList<Foe>();
        for (Foe f : KNOWN.values()) if (f.alive && f.lastSeen >= 0) out.add(f);
        return out;
    }

    // ------------------------------------------------------------------ radar

    private void driveRadar(long now) {
        if (getOthers() > 1 || target == null || now - target.lastSeen > 3) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            return;
        }
        double turn = Utils.normalRelativeAngle(target.absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(turn + (turn < 0 ? -0.4 : 0.4));
    }

    // -------------------------------------------------------------- movimento

    private void move(long now) {
        List<Foe> live = living();
        if (destination == null || me.distance(destination) < 28 || !safe.contains(destination)) {
            destination = pickSpot(live, now);
        } else if (riskAt(destination, live, now) > pickSpotRisk(live, now) * 1.15) {
            destination = pickSpot(live, now);
        }
        if (destination == null) return;

        double angle = Utils.normalRelativeAngle(
                Math.atan2(destination.x - me.x, destination.y - me.y) - getHeadingRadians());
        int drive = 1;
        if (Math.abs(angle) > Math.PI / 2) { angle = Utils.normalRelativeAngle(angle + Math.PI); drive = -1; }

        setTurnRightRadians(angle);
        setAhead(drive * me.distance(destination));
        setMaxVelocity(Math.abs(angle) > 1.2 ? 5 : 8);
    }

    private double lastBestRisk;

    private double pickSpotRisk(List<Foe> live, long now) { return lastBestRisk; }

    private Point2D.Double pickSpot(List<Foe> live, long now) {
        Point2D.Double best = null;
        double bestRisk = Double.MAX_VALUE;
        for (int a = 0; a < 32; a++) {
            double angle = a * Math.PI / 16;
            for (int r = 0; r < 3; r++) {
                double radius = 110 + r * 65;
                Point2D.Double p = project(me, angle, radius);
                if (!safe.contains(p)) continue;
                double risk = riskAt(p, live, now);
                if (risk < bestRisk) { bestRisk = risk; best = p; }
            }
        }
        lastBestRisk = bestRisk;
        return best;
    }

    /**
     * Custo de ocupar um ponto. A diferenca para um campo de risco comum esta na
     * ultima parcela: as ondas em voo pesam aqui, entao o robo tambem desvia de
     * tiro em melee, e nao so evita aglomeracao.
     */
    private double riskAt(Point2D.Double p, List<Foe> live, long now) {
        double risk = 0;

        for (int i = 0; i < live.size(); i++) {
            Foe f = live.get(i);
            double d = Math.max(p.distance(f.pos), 30);
            risk += (1 + f.energy / 60.0) * 11000.0 / (d * d);

            for (int j = i + 1; j < live.size(); j++) {
                double a1 = Math.atan2(f.pos.x - p.x, f.pos.y - p.y);
                Foe g = live.get(j);
                double a2 = Math.atan2(g.pos.x - p.x, g.pos.y - p.y);
                if (Math.abs(Utils.normalRelativeAngle(a1 - a2)) > 2.6) {
                    risk += 240.0 / Math.max(p.distance(f.pos), 60);
                }
            }
        }

        double wall = Math.min(Math.min(p.x - safe.getMinX(), safe.getMaxX() - p.x),
                               Math.min(p.y - safe.getMinY(), safe.getMaxY() - p.y));
        if (wall < 80) risk += (80 - wall) * 1.5;

        double corner = Math.min(
                Math.min(dist(p, safe.getMinX(), safe.getMinY()), dist(p, safe.getMinX(), safe.getMaxY())),
                Math.min(dist(p, safe.getMaxX(), safe.getMinY()), dist(p, safe.getMaxX(), safe.getMaxY())));
        if (corner < 200) risk += (200 - corner) * 2.0;

        risk += me.distance(p) * 0.04;
        risk += waveDanger(p, now);
        return risk;
    }

    /**
     * O que este robo tem e o TCN nao: perigo de projetil em melee.
     *
     * Para cada onda ainda em voo, calcula em que GuessFactor este ponto estaria
     * visto de quem atirou, e cobra o quanto aquele atirador ja acertou nessa
     * regiao. Onda mais iminente pesa mais.
     */
    private double waveDanger(Point2D.Double p, long now) {
        double total = 0;
        for (int i = 0; i < incoming.size(); i++) {
            EWave w = incoming.get(i);
            Foe shooter = KNOWN.get(w.shooter);
            if (shooter == null) continue;

            double reach = p.distance(w.origin);
            double travelled = w.speed * (now - w.fireTime);
            double ticks = (reach - travelled) / w.speed;
            if (ticks < 0 || ticks > 45) continue;      // ja passou ou ainda longe

            int bin = binOf(gfOf(w, p));
            double hits = 0, all = 0;
            for (int b = 0; b < BINS; b++) all += shooter.danger[b];
            hits = shooter.danger[bin];

            double share = all > 0 ? hits / all : 1.0 / BINS;
            double urgency = 1.0 / (1.0 + ticks * 0.15);
            total += 900.0 * share * urgency;
        }
        return total;
    }

    // ----------------------------------------------------------------- canhao

    private void shoot(long now) {
        if (target == null || now - target.lastSeen > 6) return;

        double power = target.distance < 200 ? 3.0 : (target.distance < 500 ? 2.2 : 1.6);
        power = Math.min(power, getEnergy() / 5);
        power = Math.min(power, target.energy / 4 + 0.1);
        if (power < 0.1) return;

        double speed = 20 - 3 * power;
        double direct = Math.atan2(target.pos.x - me.x, target.pos.y - me.y);
        double escape = Math.asin(8.0 / speed);
        int segment = segmentOf(target);

        double aim = direct;
        float[] stats = target.gf[segment];
        double total = 0;
        for (int i = 0; i < BINS; i++) total += stats[i];
        if (total < 5) { stats = target.gf[SEGMENTS]; total = 0;
            for (int i = 0; i < BINS; i++) total += stats[i]; }

        if (total >= 3) {
            int best = CENTER;
            for (int i = 0; i < BINS; i++) if (stats[i] > stats[best]) best = i;
            aim = direct + ((best - CENTER) / (double) CENTER) * escape * target.lateralDir;
        } else {
            aim = linearAim(target, speed);
        }

        double turn = Utils.normalRelativeAngle(aim - getGunHeadingRadians());
        setTurnGunRightRadians(turn);

        if (Math.abs(turn) < Math.atan(20.0 / Math.max(target.distance, 60))
                && getGunHeat() == 0 && getEnergy() > power + 0.2
                && setFireBullet(power) != null) {
            MyWave w = new MyWave();
            w.origin = new Point2D.Double(me.x, me.y);
            w.fireTime = now;
            w.speed = speed;
            w.direct = direct;
            w.escape = escape;
            w.lateralDir = target.lateralDir;
            w.segment = segment;
            w.name = target.name;
            mine.add(w);
        }
    }

    private double linearAim(Foe f, double speed) {
        double x = f.pos.x, y = f.pos.y;
        for (int t = 1; t <= 90; t++) {
            if (Math.hypot(x - me.x, y - me.y) <= speed * t) break;
            x += Math.sin(f.heading) * f.velocity;
            y += Math.cos(f.heading) * f.velocity;
            x = Math.max(18, Math.min(getBattleFieldWidth() - 18, x));
            y = Math.max(18, Math.min(getBattleFieldHeight() - 18, y));
        }
        return Math.atan2(x - me.x, y - me.y);
    }

    /** Quando a onda alcanca o alvo, registra onde ele estava. */
    private void learnFromMyWaves(long now) {
        for (Iterator<MyWave> it = mine.iterator(); it.hasNext(); ) {
            MyWave w = it.next();
            Foe f = KNOWN.get(w.name);
            if (f == null || !f.alive) { it.remove(); continue; }

            double d = w.origin.distance(f.pos);
            double radius = w.speed * (now - w.fireTime);
            if (radius < d - 18) continue;
            if (radius > d + 60) { it.remove(); continue; }

            double offset = Utils.normalRelativeAngle(
                    Math.atan2(f.pos.x - w.origin.x, f.pos.y - w.origin.y) - w.direct);
            double gf = Math.max(-1, Math.min(1, offset / w.escape * w.lateralDir));
            int bin = binOf(gf);
            for (int i = 0; i < BINS; i++) {
                float weight = (float) (1.0 / (1.0 + (i - bin) * (i - bin)));
                f.gf[w.segment][i] += weight;
                f.gf[SEGMENTS][i] += weight;
            }
            it.remove();
        }
    }

    private int segmentOf(Foe f) {
        int d = f.distance < 250 ? 0 : (f.distance < 550 ? 1 : 2);
        double l = Math.abs(f.lateral);
        int v = l < 2 ? 0 : (l < 5.5 ? 1 : 2);
        return d * 3 + v;
    }

    // ------------------------------------------------------------------ apoio

    private void expireWaves(long now) {
        for (Iterator<EWave> it = incoming.iterator(); it.hasNext(); ) {
            EWave w = it.next();
            if (w.speed * (now - w.fireTime) > me.distance(w.origin) + 60) it.remove();
        }
        while (incoming.size() > 20) incoming.remove(0);
        while (mine.size() > 40) mine.remove(0);
    }

    private double gfOf(EWave w, Point2D.Double p) {
        double offset = Utils.normalRelativeAngle(
                Math.atan2(p.x - w.origin.x, p.y - w.origin.y) - w.direct);
        return Math.max(-1, Math.min(1, offset / w.escape * w.lateralDir));
    }

    private static int binOf(double gf) {
        int b = (int) Math.round(gf * CENTER) + CENTER;
        return Math.max(0, Math.min(BINS - 1, b));
    }

    private static double dist(Point2D.Double p, double x, double y) {
        return Math.hypot(p.x - x, p.y - y);
    }

    private static Point2D.Double project(Point2D.Double from, double angle, double distance) {
        return new Point2D.Double(from.x + Math.sin(angle) * distance,
                                  from.y + Math.cos(angle) * distance);
    }

    // ------------------------------------------------------------------ dados

    private static class Foe {
        final String name;
        Point2D.Double pos;
        double distance, absBearing, heading, velocity, energy = 100, lateral;
        int lateralDir = 1;
        long lastSeen = -1;
        boolean alive = true;

        final float[][] gf = new float[SEGMENTS + 1][BINS];
        final float[]   danger = new float[BINS];

        Foe(String name) { this.name = name; }

        void newRound() {
            alive = true; lastSeen = -1; energy = 100; pos = null;
        }
    }

    private static class EWave {
        Point2D.Double origin;
        long   fireTime;
        double speed, direct, escape;
        int    lateralDir;
        String shooter;
    }

    private static class MyWave {
        Point2D.Double origin;
        long   fireTime;
        double speed, direct, escape;
        int    lateralDir, segment;
        String name;
    }
}
