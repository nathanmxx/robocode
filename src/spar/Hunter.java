package spar;

import robocode.AdvancedRobot;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;

/**
 * BOT DE TREINO - nao e o robo de competicao.
 *
 * Arquetipo "cacador": fecha distancia, mira linear, potencia alta. E de longe
 * a saida mais comum quando se pede um robo de Robocode para uma IA.
 */
public class Hunter extends AdvancedRobot {

    private String lock;
    private double lockDistance = 9999;
    private int    orbit = 1;

    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        while (true) {
            if (lock == null) setTurnRadarRight(360);
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        if (lock != null && !e.getName().equals(lock) && e.getDistance() > lockDistance + 60) return;
        lock = e.getName();
        lockDistance = e.getDistance();

        double absBearing = getHeadingRadians() + e.getBearingRadians();

        setTurnRadarRightRadians(2.0 * robocode.util.Utils.normalRelativeAngle(
                absBearing - getRadarHeadingRadians()));

        // Aproxima ate ~140 px e orbita.
        double pull = e.getDistance() > 140 ? 0.55 : -0.35;
        setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(
                absBearing - getHeadingRadians() + Math.PI / 2 * orbit - pull * orbit));
        setAhead(120 * orbit);

        // Mira linear: assume que o alvo mantem rumo e velocidade.
        double power = e.getDistance() < 200 ? 3.0 : (e.getDistance() < 400 ? 2.2 : 1.5);
        power = Math.min(power, getEnergy() / 5);
        double bulletSpeed = 20 - 3 * power;
        double time = e.getDistance() / bulletSpeed;
        double futureX = getX() + Math.sin(absBearing) * e.getDistance()
                       + Math.sin(e.getHeadingRadians()) * e.getVelocity() * time;
        double futureY = getY() + Math.cos(absBearing) * e.getDistance()
                       + Math.cos(e.getHeadingRadians()) * e.getVelocity() * time;

        double aim = Math.atan2(futureX - getX(), futureY - getY());
        setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(aim - getGunHeadingRadians()));
        if (getGunHeat() == 0 && getEnergy() > power + 0.2) setFire(power);
    }

    public void onHitWall(HitWallEvent e) {
        orbit = -orbit;
    }

    public void onRobotDeath(robocode.RobotDeathEvent e) {
        if (e.getName().equals(lock)) { lock = null; lockDistance = 9999; }
    }
}
