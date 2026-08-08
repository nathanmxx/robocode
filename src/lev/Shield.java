package lev;

import robocode.Bullet;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Defesa por projetil.
 *
 * No Robocode dois projeteis que se cruzam se destroem (onBulletHitBullet). Quase
 * ninguem usa isso, e ele habilita duas defesas que a maioria dos robos nao tem:
 *
 * SOMBRA (passiva, sempre ligada). Enquanto um projetil nosso esta no ar, ele
 * cruza a frente de onda do tiro inimigo em algum ponto. Naquele trecho da onda
 * o inimigo simplesmente nao consegue nos acertar - se o tiro dele vier por ali,
 * bate no nosso. Isso transforma parte da frente de onda em zona fisicamente
 * segura, e o wave surfing e informado disso: ele pode escolher posicoes que
 * pareceriam perigosas pela estatistica, mas estao cobertas.
 *
 * ESCUDO (ativo, oportunista). Calcular onde o projetil inimigo estara e
 * disparar contra ele para abate-lo no ar. O interceptador usa potencia minima
 * de proposito: no Robocode a velocidade do projetil e 20 - 3*potencia, entao o
 * tiro mais fraco e tambem o mais rapido e o mais barato - 0.1 de energia para
 * anular um tiro que causaria 16.
 *
 * O escudo custa caro em tempo de canhao (cada disparo esquenta o canhao por ~10
 * turnos, que e tempo sem atirar no inimigo), por isso e restrito as situacoes
 * em que levar o tiro doeria mais do que perder o troco.
 */
public class Shield {

    /** Mais rapido e mais barato: velocidade 19.7 por 0.1 de energia. */
    private static final double PARRY_POWER = 0.1;

    /** Nao vale gastar turno de canhao para anular um tiro fraco. */
    private static final double WORTH_PARRYING = 1.2;

    /** Tolerancia de encontro entre o interceptador e o alvo, em turnos. */
    private static final double TIMING_SLACK = 0.55;

    private final Leviathan bot;
    private final List<Bullet> mine = new ArrayList<Bullet>();

    private int parryAttempts;
    private int parrySuccesses;

    public Shield(Leviathan bot) {
        this.bot = bot;
    }

    public void newRound() {
        mine.clear();
    }

    public void register(Bullet b) {
        if (b != null) mine.add(b);
    }

    public void update() {
        for (Iterator<Bullet> it = mine.iterator(); it.hasNext(); ) {
            if (!it.next().isActive()) it.remove();
        }
    }

    public void onParrySuccess() {
        parrySuccesses++;
    }

    public String stats() {
        return parrySuccesses + "/" + parryAttempts;
    }

    // ------------------------------------------------------------------ sombra

    /**
     * Trechos da frente de onda cobertos por projeteis nossos, como intervalos
     * de desvio angular em relacao ao angulo direto da onda.
     *
     * Os angulos sao guardados relativos a wave.directAngle de proposito: o
     * trecho de interesse fica sempre proximo de zero, o que elimina o problema
     * de comparar angulos que dao a volta em +-PI.
     */
    public List<double[]> shadows(Wave wave, long now) {
        List<double[]> arcs = new ArrayList<double[]>();

        for (int i = 0; i < mine.size(); i++) {
            Bullet b = mine.get(i);
            if (!b.isActive()) continue;

            double x = b.getX(), y = b.getY();
            double heading = b.getHeadingRadians();
            double speed = b.getVelocity();

            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            boolean touching = false;

            for (int t = 0; t <= 60; t++) {
                double px = x + Math.sin(heading) * speed * t;
                double py = y + Math.cos(heading) * speed * t;
                if (!bot.battleField().contains(px, py)) break;

                double gap = Math.hypot(px - wave.origin.x, py - wave.origin.y)
                           - wave.radius(now + t);

                // Dentro de um passo de ambos: nesse turno os dois projeteis
                // ocupariam o mesmo trecho e colidiriam.
                if (Math.abs(gap) <= (wave.speed + speed) / 2.0) {
                    double offset = Util.relative(
                            Util.angle(wave.origin, new Point2D.Double(px, py)) - wave.directAngle);
                    min = Math.min(min, offset);
                    max = Math.max(max, offset);
                    touching = true;
                } else if (touching) {
                    break;                    // ja atravessou a frente de onda
                }
            }

            if (touching) {
                // Alarga pela espessura angular de um projetil, senao a sombra
                // fica mais fina do que a colisao real.
                double pad = Math.atan(6.0 / Math.max(
                        wave.origin.distance(new Point2D.Double(x, y)), 60));
                arcs.add(new double[] { min - pad, max + pad });
            }
        }
        return arcs;
    }

    public static boolean covered(List<double[]> arcs, double offset) {
        for (int i = 0; i < arcs.size(); i++) {
            if (offset >= arcs.get(i)[0] && offset <= arcs.get(i)[1]) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ escudo

    /**
     * Tenta abater um projetil inimigo no ar.
     *
     * Nao sabemos o angulo exato do tiro dele, mas sabemos onde ele mais acerta:
     * o pico do histograma de perigo e a mira preferida daquele robo. Assumimos
     * essa direcao, procuramos o instante em que o nosso interceptador e o tiro
     * dele estariam no mesmo ponto, e miramos la.
     *
     * Devolve true se disparou o interceptador.
     */
    public boolean tryParry(Wave wave, Enemy shooter, long now) {
        if (wave == null || shooter == null) return false;
        if (wave.power < WORTH_PARRYING) return false;
        if (bot.getGunHeat() > 0) return false;
        if (bot.getEnergy() < 3.0) return false;

        double bulletAngle = wave.angleFor(favouriteGuessFactor(shooter));
        double ourSpeed = Util.bulletSpeed(PARRY_POWER);
        Point2D.Double me = bot.position();

        for (int t = 2; t <= 28; t++) {
            long when = now + t;

            double travelled = wave.speed * (when - wave.fireTime);
            Point2D.Double p = Util.project(wave.origin, bulletAngle, travelled);
            if (!bot.battleField().contains(p)) return false;   // ja teria passado

            double needed = me.distance(p) / ourSpeed;
            if (Math.abs(needed - t) > TIMING_SLACK) continue;

            double turn = Util.relative(Util.angle(me, p) - bot.getGunHeadingRadians());
            if (Math.abs(turn) > 0.015) {
                bot.setTurnGunRightRadians(turn);   // ainda alinhando: espera o proximo turno
                return false;
            }

            if (bot.setFireBullet(PARRY_POWER) != null) {
                parryAttempts++;
                return true;
            }
            return false;
        }
        return false;
    }

    /** GuessFactor onde este atirador mais nos acertou: a mira predileta dele. */
    private double favouriteGuessFactor(Enemy shooter) {
        int best = Gun.CENTER;
        float bestValue = 0;
        for (int i = 0; i < Gun.BINS; i++) {
            if (shooter.surfDanger[i] > bestValue) {
                bestValue = shooter.surfDanger[i];
                best = i;
            }
        }
        // Sem historico, a aposta e mira direta - o caso mais comum.
        return bestValue <= 0 ? 0.0 : (best - Gun.CENTER) / (double) Gun.CENTER;
    }
}
