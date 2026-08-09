package teste;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;

public class T67 extends AdvancedRobot {

    // Estado do radar
    private boolean radarTravado = false;
    private double direcaoLateral = 1;   // 1 = horario, -1 = anti-horario
    private double energiaInimigo = 100;
    private String inimigoAlvo = null;
    private double distanciaInimigo = 9999;
    
    // Randomização para desvio
    private int tickDesvio = 0;
    private double distanciaFuga = 200; // distancia ideal do alvo

    public void run() {
        setColors(Color.magenta, Color.blue, Color.red, Color.white, Color.red);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        // Inicia radar girando para encontrar alguém
        while (true) {
            if (!radarTravado) {
                // Varredura ampla quando não tem alvo
                setTurnRadarRight(360);
            }
            
            // Movimento de wall-smoothing + orbita
            movimentoOrbita();
            
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        String nome = e.getName();
        double distancia = e.getDistance();
        double energia = e.getEnergy();
        
        // PRIORIZAÇÃO: escolhe alvo mais fraco e mais próximo
        // Se não tem alvo, ou encontrou um mais fraco (e razoavelmente perto), ou o atual está muito longe
        if (inimigoAlvo == null || 
            (energia < 30 && distancia < 400) || // prioridade maxima: fraco e perto
            (nome.equals(inimigoAlvo)) ||        // mantém alvo atual
            (energia < energiaInimigo * 0.7 && distancia < 600)) { // troca se muito mais fraco
            
            inimigoAlvo = nome;
            energiaInimigo = energia;
            distanciaInimigo = distancia;
            radarTravado = true;
            
            // Calcula ângulos
            double anguloAbsoluto = getHeadingRadians() + e.getBearingRadians();
            double giroCanhao = Utils.normalRelativeAngle(anguloAbsoluto - getGunHeadingRadians());
            double giroRadar = Utils.normalRelativeAngle(anguloAbsoluto - getRadarHeadingRadians());
            
            // Mira no inimigo
            setTurnGunRightRadians(giroCanhao);
            
            // Radar lock: aponta radar para onde o inimigo está
            // Ajuste fino para compensar movimento do inimigo
            double giroRadarExtra = e.getVelocity() * Math.sin(e.getHeadingRadians() - anguloAbsoluto) / distancia;
            setTurnRadarRightRadians(giroRadar + giroRadarExtra);
            
            // TIRO INTELIGENTE baseado em distancia e energia
            double poder;
            if (distancia < 150) {
                poder = Math.min(3, energia / 4 + 0.5); // perto: mata ou tira muito
            } else if (distancia < 300) {
                poder = 2; // médio
            } else if (distancia < 500) {
                poder = 1.5; // longe mas acertável
            } else {
                poder = 0.5; // muito longe: só para não desperdiçar
            }
            
            // Só atira se a mira está razoavelmente alinhada
            if (Math.abs(giroCanhao) < 0.3 && getEnergy() > 5) {
                setFire(poder);
            }
            
            // MOVIMENTO: perseguir mantendo distância ideal
            // Se está muito perto, afasta; se está longe, aproxima
            double distanciaDesejada = 250;
            double ajusteDistancia = distancia - distanciaDesejada;
            
            // Ângulo para orbitar (perpendicular ao inimigo)
            double anguloOrbita = anguloAbsoluto + Math.PI / 2 * direcaoLateral;
            
            // Ajusta distância: se muito perto, afasta; se muito longe, aproxima
            if (ajusteDistancia < -50) {
                // Muito perto! Afasta
                anguloOrbita = anguloAbsoluto + Math.PI; // vira de costas
                setAhead(50);
            } else if (ajusteDistancia > 150) {
                // Muito longe! Aproxima
                anguloOrbita = anguloAbsoluto;
                setAhead(100);
            } else {
                // Distância boa, orbita
                setAhead(80 * direcaoLateral);
            }
            
            setTurnRightRadians(Utils.normalRelativeAngle(anguloOrbita - getHeadingRadians()));
            
            // DESVIO: quando inimigo atira (energia caiu entre 0.1 e 3)
            if (energiaInimigo - energia > 0.1 && energiaInimigo - energia < 4) {
                tickDesvio = 8; // ativa desvio por 8 ticks
                direcaoLateral *= -1; // inverte orbita
                // Acelera ou freia aleatoriamente
                if (Math.random() > 0.5) {
                    setMaxVelocity(8);
                } else {
                    setMaxVelocity(2);
                    setBack(30);
                }
            }
            energiaInimigo = energia;
            
        } else {
            // Não é alvo prioritário, mas registra para awareness
            // Se muitos robôs próximos, considera fuga
            if (distancia < 150 && energia > getEnergy()) {
                // Robô forte muito perto e não é nosso alvo! Foge
                double anguloFuga = getHeadingRadians() + e.getBearingRadians() + Math.PI;
                setTurnRightRadians(Utils.normalRelativeAngle(anguloFuga - getHeadingRadians()));
                setAhead(100);
            }
        }
    }

    private void movimentoOrbita() {
        // Se não está engajado, movimento de borda (wall smoothing)
        if (inimigoAlvo == null) {
            // Fica próximo das paredes, evita centro
            double x = getX();
            double y = getY();
            double largura = getBattleFieldWidth();
            double altura = getBattleFieldHeight();
            
            // Se está no centro, move para borda
            if (x > largura * 0.3 && x < largura * 0.7 && y > altura * 0.3 && y < altura * 0.7) {
                // Move para canto mais próximo
                double anguloCantao = Math.atan2((x < largura/2 ? 50 : largura-50) - x, 
                                                 (y < altura/2 ? 50 : altura-50) - y);
                setTurnRightRadians(Utils.normalRelativeAngle(anguloCantao - getHeadingRadians()));
                setAhead(100);
            } else {
                // Já na borda, segue parede
                setAhead(100);
                // Se perto da parede, vira suavemente
                if (x < 80 || x > largura - 80 || y < 80 || y > altura - 80) {
                    setTurnRight(20);
                }
            }
        }
        
        // Reseta velocidade após desvio
        if (tickDesvio > 0) {
            tickDesvio--;
            if (tickDesvio == 0) {
                setMaxVelocity(8);
            }
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Foge na direção oposta ao tiro
        double anguloFuga = e.getBearingRadians() + getHeadingRadians() + Math.PI/2;
        setTurnRightRadians(Utils.normalRelativeAngle(anguloFuga - getHeadingRadians()));
        setAhead(80);
        direcaoLateral *= -1;
        tickDesvio = 5;
    }

    public void onHitRobot(HitRobotEvent e) {
        // Colisão! Foge imediatamente
        if (e.getEnergy() > getEnergy()) {
            // Inimigo mais forte, FUGE
            setBack(100);
            setTurnRight(90);
            inimigoAlvo = null; // desiste do alvo atual
            radarTravado = false;
        } else {
            // Inimigo mais fraco, empurra e atira
            setTurnGunRight(getHeading() - getGunHeading() + e.getBearing());
            setFire(3);
            setAhead(50);
        }
    }

    public void onHitWall(HitWallEvent e) {
        // Wall smoothing: vira paralelo à parede
        setBack(30);
        setTurnRight(45);
        direcaoLateral *= -1;
    }

    public void onRobotDeath(RobotDeathEvent e) {
        // Alvo morreu, procura outro
        if (e.getName().equals(inimigoAlvo)) {
            inimigoAlvo = null;
            radarTravado = false;
            energiaInimigo = 100;
        }
    }

    public void onWin(WinEvent e) {
        // Vitória! Celebração
        for (int i = 0; i < 50; i++) {
            setTurnRight(30);
            setTurnLeft(30);
        }
    }
}
