package spar;

import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

/**
 * BOT DE TREINO - nao e o robo de competicao.
 *
 * Arquetipo "orbitador": mantem distancia constante girando ao redor do alvo e
 * usa mira circular, que acerta em cheio quem anda em curva constante.
 */
public class Circler extends AdvancedRobot {

    private int direction = 1;
    private double lastEnemyHeading;

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

        // Orbita mantendo ~320 px.
        double correction = (e.getDistance() - 320) / 320.0;
        setTurnRightRadians(Utils.normalRelativeAngle(
                absBearing - getHeadingRadians() + Math.PI / 2 * direction
                - correction * direction));
        setAhead(100 * direction);

        double power = e.getDistance() < 250 ? 2.5 : 1.8;
        power = Math.min(power, getEnergy() / 6);

        // Mira circular: projeta a trajetoria assumindo taxa de giro constante.
        double turnRate = Utils.normalRelativeAngle(e.getHeadingRadians() - lastEnemyHeading);
        lastEnemyHeading = e.getHeadingRadians();

        double bulletSpeed = 20 - 3 * power;
        double x = getX() + Math.sin(absBearing) * e.getDistance();
        double y = getY() + Math.cos(absBearing) * e.getDistance();
        double heading = e.getHeadingRadians();

        for (int t = 0; t < 40; t++) {
            if (Math.hypot(x - getX(), y - getY()) < bulletSpeed * t) break;
            heading += turnRate;
            x += Math.sin(heading) * e.getVelocity();
            y += Math.cos(heading) * e.getVelocity();
            x = Math.max(18, Math.min(getBattleFieldWidth()  - 18, x));
            y = Math.max(18, Math.min(getBattleFieldHeight() - 18, y));
        }

        double aim = Math.atan2(x - getX(), y - getY());
        setTurnGunRightRadians(Utils.normalRelativeAngle(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && getEnergy() > power + 0.2) setFire(power);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        if (Math.random() < 0.4) direction = -direction;
    }

    public void onHitWall(robocode.HitWallEvent e) {
        direction = -direction;
    }
}
