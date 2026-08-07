package nx;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.RobotDeathEvent;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leviathan.
 *
 * O campeonato e decidido em dois jogos diferentes, e o robo troca de cerebro
 * conforme qual deles esta acontecendo:
 *
 *   - Melee (fases 1 a 3, 16 / 8 / 4 robos): quem morre cedo esta fora. Aqui o
 *     objetivo nao e vencer a batalha, e nao estar entre os primeiros caixoes.
 *     O robo foge do aglomerado, economiza energia e deixa os agressivos se
 *     matarem entre si.
 *
 *   - Duelo (semifinal e final, 2 robos): nao ha mais para onde fugir e a
 *     pontuacao passa a ser dano. Aqui entram a esquiva de ondas e o canhao
 *     estatistico, com tudo que foi aprendido sobre o oponente nas rodadas
 *     anteriores.
 *
 * O que persiste entre rodadas: o mapa de inimigos e suas estatisticas. Robocode
 * recria a instancia do robo a cada rodada mas mantem o classloader durante toda
 * a batalha, entao campos estaticos atravessam as 5 (ou 10) rodadas. E disso que
 * vem a vantagem acumulada: na rodada 1 o robo esta chutando, na rodada 5 ja
 * conhece o adversario.
 */
public class Leviathan extends AdvancedRobot {

    /** Sobrevive a batalha inteira. Nao limpar entre rodadas. */
    static final Map<String, Enemy> KNOWN = new HashMap<String, Enemy>();

    private final Point2D.Double position = new Point2D.Double();

    /** Area util do campo (descontado o corpo do robo). */
    private Rectangle2D.Double field;

    /** Area onde o movimento pode escolher destinos: recuada da parede. */
    private Rectangle2D.Double safeField;

    private final Movement movement = new Movement(this);

    /** Inimigo escolhido como alvo do canhao neste turno. */
    private Enemy target;

    // ------------------------------------------------------------------ ciclo

    public void run() {
        setColors(new Color(18, 20, 28),    // corpo
                  new Color(200, 40, 60),   // canhao
                  new Color(90, 200, 255),  // radar
                  new Color(255, 210, 90),  // projetil
                  new Color(40, 90, 140));  // arco de scan

        // As tres pecas giram de forma independente. Sem isso, girar o corpo
        // arrasta o canhao e o radar junto e a mira nunca converge.
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        field = new Rectangle2D.Double(Util.ROBOT_SIZE / 2, Util.ROBOT_SIZE / 2,
                                       getBattleFieldWidth()  - Util.ROBOT_SIZE,
                                       getBattleFieldHeight() - Util.ROBOT_SIZE);

        double inset = 45;
        safeField = new Rectangle2D.Double(field.x + inset, field.y + inset,
                                           Math.max(field.width  - 2 * inset, 1),
                                           Math.max(field.height - 2 * inset, 1));

        for (Enemy e : KNOWN.values()) {
            e.newRound();
        }
        movement.newRound();

        while (true) {
            position.setLocation(getX(), getY());

            chooseTarget();
            driveRadar();
            drive();
            aim();

            execute();
        }
    }

    // ----------------------------------------------------------------- eventos

    public void onScannedRobot(ScannedRobotEvent e) {
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy == null) {
            enemy = new Enemy(e.getName());
            KNOWN.put(e.getName(), enemy);
        }
        enemy.update(e, this);
        enemy.detectFiring();
    }

    public void onRobotDeath(RobotDeathEvent e) {
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy != null) {
            enemy.alive = false;
        }
    }

    public void onBulletHit(BulletHitEvent e) {
        // Valor exato da energia do inimigo apos o nosso dano: sem essa correcao
        // o proprio dano que causamos seria interpretado como tiro dele.
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy != null) {
            enemy.syncEnergy(e.getEnergy());
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Quem acertou ganha 3x a potencia em energia.
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy != null) {
            enemy.absorbKnownEnergyGain(Util.bulletReward(e.getPower()));
        }
    }

    public void onHitRobot(HitRobotEvent e) {
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy != null) {
            enemy.alive = true;
        }
    }

    public void onWin(WinEvent e) {
        // Parar de atirar evita perder energia a toa nos ultimos turnos.
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
    }

    public void onDeath(DeathEvent e) {
        // ponto de instrumentacao para as estatisticas por rodada
    }

    // ------------------------------------------------------------------ alvo

    /** Em melee o alvo e o mais proximo; em duelo so ha um. */
    private void chooseTarget() {
        Enemy best = null;
        double bestScore = Double.MAX_VALUE;
        long now = getTime();

        for (Enemy e : KNOWN.values()) {
            if (!e.alive || e.lastSeen < 0) continue;
            double score = e.distance;
            if (!e.isFresh(now)) score += 400;      // penaliza informacao velha
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        target = best;
    }

    private List<Enemy> liveEnemies() {
        List<Enemy> out = new ArrayList<Enemy>();
        for (Enemy e : KNOWN.values()) {
            if (e.alive && e.lastSeen >= 0) out.add(e);
        }
        return out;
    }

    // ----------------------------------------------------------------- radar

    /**
     * Em melee o radar gira sem parar: com muitos inimigos, perder um de vista
     * por varios turnos e pior do que ver todos com atraso de meia volta.
     * Em duelo ele trava no unico oponente, porque ai cada turno sem dado e um
     * turno de esquiva as cegas.
     */
    private void driveRadar() {
        long now = getTime();

        if (getOthers() > 1 || target == null || !target.isFresh(now)) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            return;
        }

        double turn = Util.relative(target.absBearing - getRadarHeadingRadians());
        // Margem para o radar passar do alvo em vez de parar em cima dele: o
        // inimigo se move entre o momento do calculo e o do giro.
        double margin = Math.atan(Util.ROBOT_SIZE / Math.max(target.distance, 100)) + 0.06;
        setTurnRadarRightRadians(turn + Util.sign(turn) * margin);
    }

    // -------------------------------------------------------------- movimento

    /**
     * Dois jogos diferentes, dois movimentos diferentes. Com mais de um inimigo
     * vivo o que importa e nao morrer, e o campo de risco cuida disso. Restando
     * um, nao ha mais para onde fugir e o jogo vira esquiva de tiro.
     */
    private void drive() {
        List<Enemy> live = liveEnemies();
        if (getOthers() > 1 || live.size() > 1) {
            movement.driveMelee(live, getTime());
        } else {
            movement.driveDuel(target, getTime());
        }
    }

    // ----------------------------------------------------------------- canhao

    /**
     * Mira provisoria: predicao linear iterativa. Convergimos o ponto de impacto
     * resolvendo "onde ele estara quando o projetil chegar la", que depende de
     * quando o projetil chega, que depende de onde ele estara.
     */
    private void aim() {
        if (target == null || getGunHeat() > 0) return;

        double power = choosePower(target);
        if (power <= 0) return;

        double speed = Util.bulletSpeed(power);
        Point2D.Double predicted = target.predictedPosition(getTime());

        for (int i = 0; i < 12; i++) {
            double time = position.distance(predicted) / speed;
            Point2D.Double next = Util.project(target.pos, target.heading, target.velocity * time);
            next.x = Util.clamp(field.getMinX(), next.x, field.getMaxX());
            next.y = Util.clamp(field.getMinY(), next.y, field.getMaxY());
            if (next.distance(predicted) < 0.5) {
                predicted = next;
                break;
            }
            predicted = next;
        }

        double gunTurn = Util.relative(Util.angle(position, predicted) - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        // So dispara com o canhao praticamente alinhado: tiro torto e energia
        // jogada fora, e energia e vida.
        if (Math.abs(gunTurn) < 0.06 && getEnergy() > power + 0.4) {
            setFire(power);
        }
    }

    /**
     * Energia e vida: cada tiro custa energia e o robo so a recupera acertando.
     * Em melee, onde a taxa de acerto e naturalmente baixa, atirar forte e
     * sangrar devagar. Por isso a potencia cai com o numero de inimigos vivos.
     */
    private double choosePower(Enemy t) {
        double power;

        if (getOthers() > 1) {
            power = t.distance < 250 ? 1.9 : 1.2;
        } else {
            power = t.distance < 200 ? 3.0 : (t.distance < 450 ? 2.2 : 1.6);
        }

        // Nunca gastar mais energia do que sobra com folga.
        power = Math.min(power, getEnergy() / 6.0);
        // Nem mais do que o necessario para derrubar o alvo.
        power = Math.min(power, t.energy / 4.0 + 0.1);

        if (getEnergy() < 4.0) power = Math.min(power, 0.5);
        if (power < 0.1) return 0;

        return Util.clamp(0.1, power, 3.0);
    }

    // ------------------------------------------------------------------ apoio

    Point2D.Double myPosition() {
        return new Point2D.Double(getX(), getY());
    }

    /** Posicao atual, atualizada uma vez por turno. Nao alocar por chamada. */
    Point2D.Double position() {
        return position;
    }

    Rectangle2D.Double safeField() {
        return safeField;
    }

    Rectangle2D.Double battleField() {
        return field;
    }
}
