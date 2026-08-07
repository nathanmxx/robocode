package spar;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

/**
 * BOT DE TREINO - nao e o robo de competicao.
 *
 * Arquetipo "sniper de canto": foge para longe, cola na borda e atira de longe.
 * E o arquetipo que mais se parece com a nossa propria estrategia de
 * sobrevivencia, entao serve de sparring direto para as fases de melee.
 */
public class Sniper extends AdvancedRobot {

    private int direction = 1;

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

        // Mantem 500 px: afasta se estiver perto, orbita se ja estiver longe.
        double correction = (e.getDistance() - 500) / 400.0;
        setTurnRightRadians(Utils.normalRelativeAngle(
                absBearing - getHeadingRadians() + Math.PI / 2 * direction - correction * direction));

        double nextX = getX() + Math.sin(getHeadingRadians()) * 70 * direction;
        double nextY = getY() + Math.cos(getHeadingRadians()) * 70 * direction;
        if (nextX < 45 || nextY < 45 || nextX > getBattleFieldWidth() - 45
                || nextY > getBattleFieldHeight() - 45) {
            direction = -direction;
        }
        setAhead(90 * direction);

        double power = Math.min(1.9, getEnergy() / 7);
        double bulletSpeed = 20 - 3 * power;
        double time = e.getDistance() / bulletSpeed;
        double aimX = getX() + Math.sin(absBearing) * e.getDistance()
                    + Math.sin(e.getHeadingRadians()) * e.getVelocity() * time;
        double aimY = getY() + Math.cos(absBearing) * e.getDistance()
                    + Math.cos(e.getHeadingRadians()) * e.getVelocity() * time;

        double aim = Math.atan2(aimX - getX(), aimY - getY());
        setTurnGunRightRadians(Utils.normalRelativeAngle(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && getEnergy() > power + 0.5) setFire(power);
    }
}
