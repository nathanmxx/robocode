package spar;

import robocode.AdvancedRobot;
import robocode.HitRobotEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

/**
 * BOT DE TREINO - nao e o robo de competicao.
 *
 * Arquetipo "ramador": vai para cima e bate. Ramming da 2 pontos por dano e
 * bonus de 30% na eliminacao, entao sempre aparece alguem tentando. Serve para
 * testar se o nosso robo consegue manter distancia sob perseguicao.
 */
public class Rammer extends AdvancedRobot {

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

        setTurnRightRadians(Utils.normalRelativeAngle(absBearing - getHeadingRadians()));
        setAhead(e.getDistance() + 20);

        setTurnGunRightRadians(Utils.normalRelativeAngle(absBearing - getGunHeadingRadians()));
        if (getGunHeat() == 0 && e.getDistance() < 120 && getEnergy() > 3.2) setFire(3);
    }

    public void onHitRobot(HitRobotEvent e) {
        setTurnRightRadians(Utils.normalRelativeAngle(
                getHeadingRadians() + e.getBearingRadians() - getHeadingRadians()));
        setAhead(40);
        if (getGunHeat() == 0 && getEnergy() > 3.2) setFire(3);
    }
}
