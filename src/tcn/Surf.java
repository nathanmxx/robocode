package tcn;

import robocode.HitByBulletEvent;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Wave surfing.
 *
 * Esquivar "para o lado" e chute. O que este movimento faz e outra coisa: para
 * cada tiro inimigo em voo, ele simula a propria fisica do robo nos dois
 * sentidos de orbita possiveis, descobre em que ponto da frente de onda cada
 * escolha o colocaria, e vai para o lado onde esse inimigo historicamente menos
 * acerta.
 *
 * O mapa de perigo e construido com os proprios tiros recebidos: cada acerto
 * ensina onde nao estar. Como o mapa sobrevive entre rodadas, o robo fica
 * progressivamente mais dificil de acertar ao longo das 10 rodadas da final -
 * exatamente quando isso vale mais.
 *
 * So opera em duelo. Com 15 inimigos vivos ha ondas demais para surfar e a
 * deteccao de disparo por queda de energia fica ruidosa; nesse caso quem manda
 * e o campo de risco.
 */
public class Surf {

    /** Alem disso a simulacao nao vale a pena: a onda ja chegou ou se perdeu. */
    private static final int MAX_SIM_TICKS = 220;

    /** Distancia de orbita perseguida quando nao ha nenhuma onda em voo. */
    private static final double IDLE_ORBIT_DISTANCE = 420;

    private final TCN bot;
    private final List<Wave> incoming = new ArrayList<Wave>();

    public Surf(TCN bot) {
        this.bot = bot;
    }

    public void newRound() {
        incoming.clear();
    }

    // ------------------------------------------------------- ciclo de vida das ondas

    /**
     * Registra um tiro inimigo recem detectado. A origem e a posicao ANTERIOR
     * dele: quando percebemos a queda de energia, o projetil ja saiu no turno
     * passado, daquele ponto.
     */
    public void onEnemyFire(Enemy e) {
        if (e.justFiredPower <= 0) return;

        Wave w = new Wave();
        w.origin = new Point2D.Double(e.previousPos.x, e.previousPos.y);
        w.fireTime = e.lastSeen - 1;
        w.power = e.justFiredPower;
        w.speed = Util.bulletSpeed(e.justFiredPower);
        w.other = e.name;
        w.directAngle = Util.angle(w.origin, bot.position());
        w.escapeAngle = Util.maxEscapeAngle(w.speed);

        // Do ponto de vista dele, para que lado nos orbitamos.
        double lateral = bot.getVelocity()
                * Math.sin(bot.getHeadingRadians() - w.directAngle);
        w.lateralDirection = Util.sign(lateral == 0 ? 1 : lateral);

        // Onde uma mira linear colocaria este tiro: como o GuessFactor ja esta
        // normalizado pelo sentido de orbita e pela velocidade maxima, a fracao
        // da velocidade lateral e diretamente o GF que ele teria escolhido.
        w.priorGuessFactor = Math.abs(lateral) / Util.MAX_VELOCITY;

        incoming.add(w);
    }

    public void update(long now) {
        for (Iterator<Wave> it = incoming.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            if (w.radius(now) > bot.position().distance(w.origin) + 50) {
                it.remove();                       // passou por nos
            }
        }
        while (incoming.size() > 12) incoming.remove(0);
    }

    /** Onda mais iminente: a que esta mais perto de nos alcancar. */
    public Wave closestWave(long now) {
        Wave best = null;
        double bestGap = Double.MAX_VALUE;
        Point2D.Double me = bot.position();

        for (int i = 0; i < incoming.size(); i++) {
            Wave w = incoming.get(i);
            double gap = me.distance(w.origin) - w.radius(now);
            if (gap > -10 && gap < bestGap) {
                bestGap = gap;
                best = w;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ aprender

    public void onHitByBullet(HitByBulletEvent event, Enemy shooter, long now) {
        if (shooter == null) return;

        Point2D.Double hitAt = bot.position();
        Wave matched = null;
        double bestError = Double.MAX_VALUE;

        // Casa o acerto com a onda cujo raio bate com a distancia percorrida.
        for (int i = 0; i < incoming.size(); i++) {
            Wave w = incoming.get(i);
            if (!w.other.equals(shooter.name)) continue;
            double error = Math.abs(w.radius(now) - hitAt.distance(w.origin))
                         + Math.abs(w.power - event.getPower()) * 40;
            if (error < bestError) {
                bestError = error;
                matched = w;
            }
        }
        if (matched == null || bestError > 60) return;

        int bin = binOf(matched.guessFactor(hitAt));

        // Um acerto condena o bin exato e, cada vez menos, a vizinhanca: se ele
        // acertou aqui, chegar perto daqui de novo tambem e arriscado.
        for (int i = 0; i < Gun.BINS; i++) {
            shooter.surfDanger[i] += (float) (1.0 / (1.0 + (i - bin) * (i - bin)));
        }
        incoming.remove(matched);
    }

    // ------------------------------------------------------------------ dirigir

    public void drive(Enemy target, long now) {
        Wave wave = closestWave(now);

        if (wave == null || target == null) {
            orbit(target, now);
            return;
        }

        // Trechos da onda cobertos por projeteis nossos: la ele nao acerta,
        // porque o tiro dele bateria no nosso antes.
        List<double[]> shadows = TCN.USE_BULLET_SHADOW
                ? bot.shield().shadows(wave, now)
                : java.util.Collections.<double[]>emptyList();

        double dangerLeft  = dangerOf(wave, -1, now, target, shadows);
        double dangerRight = dangerOf(wave, +1, now, target, shadows);

        int direction = dangerLeft < dangerRight ? -1 : +1;
        steerAround(wave.origin, direction);
    }

    /** Para onde iriamos parar surfando nesse sentido, e o quao perigoso e isso. */
    private double dangerOf(Wave wave, int direction, long now, Enemy target,
                            List<double[]> shadows) {
        Point2D.Double landing = simulate(wave, direction, now);

        double offset = Util.relative(Util.angle(wave.origin, landing) - wave.directAngle);
        if (Shield.covered(shadows, offset)) {
            // Coberto por projetil nosso: e seguro por fisica, nao por estimativa.
            return 30.0 / Math.max(landing.distance(wave.origin), 60);
        }

        int bin = binOf(wave.guessFactor(landing));

        double total = 0;
        for (int i = 0; i < Gun.BINS; i++) total += target.surfDanger[i];

        double learned = total > 0 ? target.surfDanger[bin] / total : 0;

        // A confianca na estatistica cresce com os acertos observados. Ate la
        // vale o palpite: a maioria dos robos mira direto ou linear.
        double confidence = Util.clamp(0, total / 15.0, 1.0);
        double danger = confidence * learned + (1 - confidence) * priorDanger(wave, bin);

        // Desempate: ficar longe. Mais tempo de voo, mais chance de ele errar.
        danger += 30.0 / Math.max(landing.distance(wave.origin), 60);
        return danger;
    }

    /**
     * Perigo presumido antes de haver estatistica. Concentra-se em dois pontos:
     * onde uma mira linear colocaria o tiro e onde uma mira direta colocaria.
     *
     * Sem isso o surfing comeca cego e escolhe o lado quase por sorteio - o que
     * e sensivelmente pior do que a heuristica ingenua de inverter o sentido a
     * cada disparo detectado. O palpite faz o robo ja nascer esquivando bem e ir
     * refinando conforme apanha.
     */
    private double priorDanger(Wave wave, int bin) {
        int linearBin = binOf(wave.priorGuessFactor);
        double dLinear = bin - linearBin;
        double dHeadOn = bin - Gun.CENTER;
        return 1.0 / (1.0 + dLinear * dLinear * 0.5)
             + 0.6 / (1.0 + dHeadOn * dHeadOn * 0.5);
    }

    private static int binOf(double guessFactor) {
        return (int) Util.clamp(0, Math.round(guessFactor * Gun.CENTER) + Gun.CENTER, Gun.BINS - 1);
    }

    /**
     * Simula a propria fisica do Robocode turno a turno ate a onda alcancar o
     * robo. Sem reproduzir aceleracao e taxa de giro reais, a previsao de onde
     * vamos parar erra o suficiente para a esquiva virar sorte.
     */
    private Point2D.Double simulate(Wave wave, int direction, long now) {
        Point2D.Double p = new Point2D.Double(bot.getX(), bot.getY());
        double heading  = bot.getHeadingRadians();
        double velocity = bot.getVelocity();

        for (int tick = 1; tick <= MAX_SIM_TICKS; tick++) {
            double desired = orbitAngle(p, wave.origin, direction);

            double turn = Util.relative(desired - heading);
            double drive = 1;
            if (Math.cos(turn) < 0) {           // mais barato ir de re
                turn = Util.relative(turn + Math.PI);
                drive = -1;
            }

            // Taxa de giro do Robocode: 10 - 0.75*|v| graus por turno.
            double maxTurn = Math.PI / 720.0 * (40.0 - 3.0 * Math.abs(velocity));
            heading = Util.relative(heading + Util.clamp(-maxTurn, turn, maxTurn));

            // Acelera 1 por turno, mas freia 2 quando inverte o sentido.
            velocity += (velocity * drive < 0) ? 2.0 * drive : drive;
            velocity = Util.clamp(-Util.MAX_VELOCITY, velocity, Util.MAX_VELOCITY);

            p = Util.project(p, heading, velocity);

            if (p.distance(wave.origin) <= wave.radius(now + tick) + wave.speed) {
                break;                          // a onda nos alcancou aqui
            }
        }
        return p;
    }

    /**
     * Desvia o angulo desejado ate que seguir por ele nao termine na parede.
     * Bater na parede custa energia e, pior, cola o robo num lugar previsivel.
     */
    private double smoothAgainstWalls(Point2D.Double from, double angle, int direction) {
        Rectangle2D.Double safe = bot.safeField();
        double stick = 150;
        int guard = 0;
        while (!safe.contains(Util.project(from, angle, stick)) && guard++ < 100) {
            angle += direction * 0.05;
        }
        return angle;
    }

    /**
     * Angulo de orbita em torno de um centro, com manutencao de distancia.
     *
     * A orbita puramente tangencial mantem o raio so no papel: contra um
     * adversario que avanca, ela deixa o robo ser puxado para o corpo a corpo,
     * onde ate a mira mais burra acerta e ainda se leva dano de colisao. O termo
     * de correcao empurra para fora quando a distancia cai.
     *
     * A mesma funcao serve a simulacao e ao movimento real - se a previsao usar
     * uma regra e o robo outra, a esquiva vira sorte.
     */
    private double orbitAngle(Point2D.Double from, Point2D.Double center, int direction) {
        double radialOut = Util.angle(center, from);
        double correction = Util.clamp(-0.6,
                (IDLE_ORBIT_DISTANCE - from.distance(center)) / 400.0, 0.6);
        return smoothAgainstWalls(from,
                radialOut + Util.HALF_PI * direction - correction * direction, direction);
    }

    /** Aplica no robo real o sentido de orbita escolhido. */
    private void steerAround(Point2D.Double center, int direction) {
        double desired = orbitAngle(bot.position(), center, direction);

        double turn = Util.relative(desired - bot.getHeadingRadians());
        int drive = 1;
        if (Math.cos(turn) < 0) {
            turn = Util.relative(turn + Math.PI);
            drive = -1;
        }
        bot.setTurnRightRadians(turn);
        bot.setAhead(drive * 100);
        bot.setMaxVelocity(Util.MAX_VELOCITY);
    }

    /** Sem onda em voo: orbita mantendo distancia, pronto para reagir. */
    private void orbit(Enemy target, long now) {
        if (target == null) {
            Rectangle2D.Double safe = bot.safeField();
            steerAround(new Point2D.Double(safe.getCenterX(), safe.getCenterY()), 1);
            return;
        }

        // absBearing aponta de nos para ele: subtrair a correcao aproxima.
        double correction = Util.clamp(-0.7,
                (target.distance - IDLE_ORBIT_DISTANCE) / 400.0, 0.7);
        double desired = smoothAgainstWalls(bot.position(),
                target.absBearing + Util.HALF_PI * idleDirection - correction * idleDirection,
                idleDirection);

        double turn = Util.relative(desired - bot.getHeadingRadians());
        int drive = 1;
        if (Math.cos(turn) < 0) {
            turn = Util.relative(turn + Math.PI);
            drive = -1;
        }
        bot.setTurnRightRadians(turn);
        bot.setAhead(drive * 100);
        bot.setMaxVelocity(Util.MAX_VELOCITY);

        if (now - lastIdleFlip > 40) {           // nao virar uma orbita previsivel
            idleDirection = -idleDirection;
            lastIdleFlip = now;
        }
    }

    private int  idleDirection = 1;
    private long lastIdleFlip = 0;
}
