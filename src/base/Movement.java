package base;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Movimento por campo de risco (minimum risk movement).
 *
 * A ideia: em vez de decidir "para onde ir" com uma regra fixa (orbitar, fugir,
 * perseguir), o robo espalha pontos candidatos ao seu redor, atribui um custo a
 * cada um e vai para o mais barato. O comportamento emerge do custo — perto de
 * muita gente fica caro, canto fica caro, ficar parado na mesma linha de tiro
 * fica caro — e o resultado e um movimento erratico que nenhum canhao de padrao
 * consegue prever, sem que exista um "padrao de fuga" para o inimigo aprender.
 *
 * Essa e a peca que decide as fases 1 a 3 do campeonato: com 16 robos na arena,
 * quem sobrevive nao e quem atira melhor, e quem nunca esta no lugar errado.
 */
public class Movement {

    /** Direcoes testadas em torno da posicao atual. */
    private static final int ANGLE_STEPS = 32;

    /**
     * O destino so muda se o novo candidato for sensivelmente melhor. Sem essa
     * histerese o robo fica vibrando entre dois pontos de custo quase igual e
     * na pratica nao sai do lugar — o que e a pior coisa possivel.
     */
    private static final double SWITCH_MARGIN = 0.92;

    private final Base bot;

    private Point2D.Double destination;
    private double destinationRisk = Double.MAX_VALUE;

    public Movement(Base bot) {
        this.bot = bot;
    }

    public void newRound() {
        destination = null;
        destinationRisk = Double.MAX_VALUE;
    }

    // ------------------------------------------------------------------ melee

    public void driveMelee(List<Enemy> live, long now) {
        Rectangle2D.Double safe = bot.safeField();

        if (destination != null) {
            // Reavalia o destino atual: o campo de risco muda a cada turno.
            destinationRisk = riskAt(destination, live, now);
            if (!safe.contains(destination) || bot.position().distance(destination) < 30) {
                destination = null;
                destinationRisk = Double.MAX_VALUE;
            }
        }

        Point2D.Double best = null;
        double bestRisk = Double.MAX_VALUE;
        double[] radii = bot.profile().candidateRadii;

        for (int i = 0; i < ANGLE_STEPS; i++) {
            double angle = i * Util.TWO_PI / ANGLE_STEPS;
            for (int r = 0; r < radii.length; r++) {
                Point2D.Double candidate = Util.project(bot.position(), angle, radii[r]);
                if (!safe.contains(candidate)) continue;

                double risk = riskAt(candidate, live, now);
                if (risk < bestRisk) {
                    bestRisk = risk;
                    best = candidate;
                }
            }
        }

        if (best != null && (destination == null || bestRisk < destinationRisk * SWITCH_MARGIN)) {
            destination = best;
            destinationRisk = bestRisk;
        }

        if (destination == null) {
            // Cercado por todos os lados: vai para o centro, que ao menos tem saida.
            destination = new Point2D.Double(safe.getCenterX(), safe.getCenterY());
        }

        goTo(destination);
    }

    /**
     * Custo de ocupar um ponto. Cada parcela representa uma forma concreta de
     * morrer em uma arena lotada.
     */
    private double riskAt(Point2D candidate, List<Enemy> live, long now) {
        double risk = 0;
        Point2D.Double here = bot.position();
        Profile p = bot.profile();

        for (int i = 0; i < live.size(); i++) {
            Enemy e = live.get(i);
            Point2D.Double ep = e.predictedPosition(now);

            double d = Math.max(candidate.distance(ep), 25);

            // 1. Proximidade. Inimigo com muita energia atira mais forte e
            //    aguenta mais, entao pesa mais. O quadrado da distancia faz o
            //    custo explodir de perto, o que na pratica e o anti-ram.
            double threat = (1.0 + Util.clamp(0, e.energy, 150) / 60.0) * p.survivalBias;
            risk += threat * 12000.0 / (d * d);

            // 2. Nao repetir angulo. Se o candidato esta na mesma direcao radial
            //    em que ja estamos em relacao a esse inimigo, chegar la nao muda
            //    a mira dele: um tiro direto continua valendo. Mover-se
            //    perpendicular e o que obriga o adversario a recalcular.
            double delta = Util.angle(ep, candidate) - Util.angle(ep, here);
            risk += threat * 90.0 * Math.abs(Math.cos(delta)) / Math.sqrt(d);

            // 3. Fogo cruzado. Ficar entre dois inimigos e o pior lugar da
            //    arena: os dois miram e um erra no outro... acertando em nos.
            for (int j = i + 1; j < live.size(); j++) {
                Point2D.Double op = live.get(j).predictedPosition(now);
                double a1 = Util.angle(candidate, ep);
                double a2 = Util.angle(candidate, op);
                double between = Math.abs(Util.relative(a1 - a2));
                if (between > 2.6) {                       // quase 180 graus
                    risk += 260.0 / Math.max(candidate.distance(ep), 60);
                }
            }
        }

        // 4. Paredes e cantos. A parede em si nao mata (3 de dano), mas encosta
        //    contra ela metade das rotas de fuga desaparece. Canto e sentenca.
        Rectangle2D.Double f = bot.safeField();
        double wallGap = Math.min(
                Math.min(candidate.getX() - f.getMinX(), f.getMaxX() - candidate.getX()),
                Math.min(candidate.getY() - f.getMinY(), f.getMaxY() - candidate.getY()));
        if (wallGap < p.wallGap) risk += (p.wallGap - wallGap) * 1.6;

        double cornerGap = Math.min(
                Math.min(dist(candidate, f.getMinX(), f.getMinY()), dist(candidate, f.getMinX(), f.getMaxY())),
                Math.min(dist(candidate, f.getMaxX(), f.getMinY()), dist(candidate, f.getMaxX(), f.getMaxY())));
        if (cornerGap < p.cornerGap) risk += (p.cornerGap - cornerGap) * 2.2;

        // 5. Custo de deslocamento. Atravessar a arena para chegar num ponto
        //    otimo geralmente significa cruzar a zona de tiro de todo mundo.
        risk += here.distance(candidate) * 0.05;

        return risk;
    }

    private static double dist(Point2D p, double x, double y) {
        return Math.hypot(p.getX() - x, p.getY() - y);
    }

    // ------------------------------------------------------------------ duelo

    /**
     * Duelo, versao provisoria: orbita mantendo distancia e inverte o sentido
     * quando detecta um disparo. Sera substituido pelo wave surfing.
     */
    public void driveDuel(Enemy target, long now) {
        if (target == null) {
            goTo(new Point2D.Double(bot.safeField().getCenterX(), bot.safeField().getCenterY()));
            return;
        }

        double desired = target.absBearing + Util.HALF_PI * duelDirection;
        double correction = (target.distance - 420) / 500.0;
        desired -= correction * duelDirection;

        Point2D.Double ahead = Util.project(bot.position(), desired, 150);
        if (!bot.safeField().contains(ahead)) {
            duelDirection = -duelDirection;
            desired = target.absBearing + Util.HALF_PI * duelDirection;
        }

        if (target.justFiredPower > 0 && now - lastFlip > 4) {
            duelDirection = -duelDirection;
            lastFlip = now;
        }

        double turn = Util.relative(desired - bot.getHeadingRadians());
        int direction = 1;
        if (Math.abs(turn) > Util.HALF_PI) {
            turn = Util.relative(turn + Math.PI);
            direction = -1;
        }
        bot.setTurnRightRadians(turn);
        bot.setAhead(direction * 120);
        bot.setMaxVelocity(Util.MAX_VELOCITY);
    }

    private int  duelDirection = 1;
    private long lastFlip = -99;

    // ------------------------------------------------------------------- apoio

    /** Dirige em linha reta ate o ponto, de frente ou de re, o que exigir menos giro. */
    private void goTo(Point2D dest) {
        double angle = Util.relative(Util.angle(bot.position(), dest) - bot.getHeadingRadians());
        double distance = bot.position().distance(dest);

        int direction = 1;
        if (Math.abs(angle) > Util.HALF_PI) {
            angle = Util.relative(angle + Math.PI);
            direction = -1;
        }

        bot.setTurnRightRadians(angle);
        bot.setAhead(distance * direction);

        // Em curva muito fechada, andar devagar aumenta o raio de giro util:
        // o Robocode reduz a taxa de rotacao conforme a velocidade sobe.
        bot.setMaxVelocity(Math.abs(angle) > 1.2 ? 5.0 : Util.MAX_VELOCITY);
    }
}
