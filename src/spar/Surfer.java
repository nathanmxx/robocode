package spar;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

/**
 * BOT DE TREINO - nao e o robo de competicao.
 *
 * Arquetipo mais dificil: nao anda em linha nem em curva constante. Inverte o
 * sentido no exato turno em que detecta um disparo inimigo (queda de energia),
 * que e justamente quando a mira do adversario ja foi calculada e travada.
 * Mira linear com correcao de parede.
 */
public class Surfer extends AdvancedRobot {

    private int direction = 1;
    private double previousEnergy = 100;
    private long lastReverse = -99;

    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        while (true) {
            setTurnRadarRight(360);
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double absBearing = getHeadingRadians() + e.getBearingRadians();
        setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);

        // Um projetil nasceu: inverter agora invalida a predicao que ele acabou de fazer.
        double drop = previousEnergy - e.getEnergy();
        previousEnergy = e.getEnergy();
        if (drop > 0.09 && drop <= 3.0 && getTime() - lastReverse > 4) {
            direction = -direction;
            lastReverse = getTime();
        }

        // Perpendicular ao inimigo, com leve atracao/repulsao para segurar a distancia.
        double correction = (e.getDistance() - 400) / 500.0;
        setTurnRightRadians(Utils.normalRelativeAngle(
                absBearing - getHeadingRadians() + Math.PI / 2 * direction - correction * direction));

        // Longe das paredes: se a proxima posicao sai do campo, inverte.
        double nextX = getX() + Math.sin(getHeadingRadians()) * 60 * direction;
        double nextY = getY() + Math.cos(getHeadingRadians()) * 60 * direction;
        if (nextX < 50 || nextY < 50 || nextX > getBattleFieldWidth() - 50
                || nextY > getBattleFieldHeight() - 50) {
            direction = -direction;
        }
        setAhead(100 * direction);

        double power = e.getDistance() < 250 ? 2.4 : 1.6;
        power = Math.min(power, getEnergy() / 6);
        double bulletSpeed = 20 - 3 * power;
        double time = e.getDistance() / bulletSpeed;
        double aimX = getX() + Math.sin(absBearing) * e.getDistance()
                    + Math.sin(e.getHeadingRadians()) * e.getVelocity() * time;
        double aimY = getY() + Math.cos(absBearing) * e.getDistance()
                    + Math.cos(e.getHeadingRadians()) * e.getVelocity() * time;
        aimX = Math.max(18, Math.min(getBattleFieldWidth()  - 18, aimX));
        aimY = Math.max(18, Math.min(getBattleFieldHeight() - 18, aimY));

        double aim = Math.atan2(aimX - getX(), aimY - getY());
        setTurnGunRightRadians(Utils.normalRelativeAngle(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && getEnergy() > power + 0.2) setFire(power);
    }
}
