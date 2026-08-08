package tcn;

import robocode.AdvancedRobot;
import robocode.BulletHitBulletEvent;
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
 * TCN.
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
public class TCN extends AdvancedRobot {

    /** Sobrevive a batalha inteira. Nao limpar entre rodadas. */
    static final Map<String, Enemy> KNOWN = new HashMap<String, Enemy>();

    /** Chaves de A/B: medir uma defesa de cada vez, nunca as duas juntas. */
    static final boolean USE_BULLET_SHADOW = false;
    static final boolean USE_ACTIVE_PARRY  = false;

    private final Point2D.Double position = new Point2D.Double();

    /** Area util do campo (descontado o corpo do robo). */
    private Rectangle2D.Double field;

    /** Area onde o movimento pode escolher destinos: recuada da parede. */
    private Rectangle2D.Double safeField;

    /** Ajustes derivados do tamanho da arena e da fase do campeonato. */
    private Profile profile;

    private final Movement movement = new Movement(this);
    private final Gun gun = new Gun(this);
    private final Surf surf = new Surf(this);
    private final Shield shield = new Shield(this);

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

        // getOthers() no inicio da rodada da a quantidade de adversarios, que e o
        // que identifica a fase: 15 na Fase 1, 7 na 2, 3 na 3, 1 na final.
        profile = Profile.forBattle(getBattleFieldWidth(), getBattleFieldHeight(), getOthers());

        double inset = profile.safeInset;
        safeField = new Rectangle2D.Double(field.x + inset, field.y + inset,
                                           Math.max(field.width  - 2 * inset, 1),
                                           Math.max(field.height - 2 * inset, 1));

        for (Enemy e : KNOWN.values()) {
            e.newRound();
        }
        movement.newRound();
        gun.newRound();
        surf.newRound();
        shield.newRound();

        while (true) {
            position.setLocation(getX(), getY());

            gun.update(getTime());     // ondas que chegaram no alvo viram estatistica
            surf.update(getTime());    // ondas inimigas que ja passaram sao descartadas
            shield.update();           // projeteis nossos que sairam de cena
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

        // Ondas inimigas so em duelo: com a arena cheia, a queda de energia
        // confunde tiro com colisao e o radar girando deixa os dados velhos.
        if (isDuel()) {
            surf.onEnemyFire(enemy);
        }
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
            surf.onHitByBullet(e, enemy, getTime());
        }
    }

    /**
     * Um projetil nosso anulou um projetil inimigo. Conta tanto a interceptacao
     * deliberada quanto a colisao que a sombra provoca por conta propria.
     */
    public void onBulletHitBullet(BulletHitBulletEvent e) {
        shield.onParrySuccess();
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

    /**
     * O alvo e o mais proximo, e so.
     *
     * MEDIDO: preferir inimigos com pouca energia perde. Testado pareado em 200
     * rodadas, ponderar a distancia pela fragilidade do alvo deu 169340 contra
     * 175587, e 67 rodadas vencidas contra 90.
     *
     * O raciocinio que motivou a tentativa estava errado. Cada morte rende 50 de
     * survival a TODOS que continuam vivos, nao a quem matou - entao terminar um
     * ferido nao traz nada exclusivo alem dos 20% de bonus, e isso nao paga
     * girar o canhao para um alvo mais distante e perder taxa de acerto. Alem
     * disso o mais proximo e tambem o que mais ameaca a gente.
     */
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
        if (isDuel()) {
            surf.drive(target, getTime());
        } else {
            movement.driveMelee(liveEnemies(), getTime());
        }
    }

    /** Sobrou um: acabou a fuga, comeca a esquiva. */
    boolean isDuel() {
        return getOthers() <= 1;
    }

    // ----------------------------------------------------------------- canhao

    private void aim() {
        // Atirar em informacao velha e energia jogada fora. Em melee o radar gira
        // e cada inimigo e visto a cada meia volta, entao a tolerancia acompanha.
        if (target == null || (getTime() - target.lastSeen) > 10) return;

        // O canhao e um recurso unico: usar para interceptar e abrir mao do
        // troco naquele turno. So compensa quando levar o tiro doeria mais.
        if (shouldParry() && shield.tryParry(surf.closestWave(getTime()), target, getTime())) {
            return;
        }

        gun.engage(target, choosePower(target), getTime());
    }

    /**
     * De longe e com energia sobrando, atirar de volta rende mais que defender.
     * De perto a chance de ele acertar dispara, e com pouca energia cada acerto
     * evitado vale mais que cada acerto dado - abaixo de um certo ponto, um
     * tiro de potencia 3 e a diferenca entre continuar na arena e sair dela.
     */
    private boolean shouldParry() {
        if (!USE_ACTIVE_PARRY || !isDuel() || target == null) return false;
        return target.distance < 250 || getEnergy() < 25;
    }

    /**
     * Energia e vida: cada tiro custa energia e o robo so a recupera acertando.
     * Em melee, onde a taxa de acerto e naturalmente baixa, atirar forte e
     * sangrar devagar. Por isso a potencia cai com o numero de inimigos vivos.
     */
    private double choosePower(Enemy t) {
        double power;

        if (getOthers() > 1) {
            power = t.distance < profile.gunNear ? 1.9 : 1.2;
        } else {
            power = t.distance < profile.pointBlank ? 3.0
                  : (t.distance < profile.gunFar ? 2.2 : 1.6);
        }
        power *= profile.firePowerScale;

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

    Shield shield() {
        return shield;
    }

    Profile profile() {
        return profile;
    }
}
